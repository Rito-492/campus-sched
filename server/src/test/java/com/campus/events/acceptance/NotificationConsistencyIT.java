package com.campus.events.acceptance;

import com.campus.events.support.DbTestSupport;
import com.campus.events.support.TestClockConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-AT-19 通知一致性（TC-19-1：消费失败恢复 + 重复消费不重发）。
 *
 * <p>装载数据：{@code demo.json}。业务事件由活动 A=4001 创建改期产生（同事务追加 outbox 事件）。
 * 固定 Clock=2030-10-19T00:00:00Z、DEMO_DATE=2030-10-20（03 §1）。
 *
 * <p>失败注入依赖 PM/CM 的装配：生产通知消费者在投递到每个收件人前必须调用
 * {@link NotificationDeliveryProbe#beforeDelivery(UUID, UUID)}；本类以 {@code @Primary} 覆盖提供
 * 「首次抛异常、随后放行」的测试探针，不修改生产代码、不提供任何公网故障接口（03 §2）。
 *
 * <p>执行命令：{@code ./server/mvnw.cmd -f server/pom.xml "-Dit.test=NotificationConsistencyIT" verify}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import({TestClockConfiguration.class, NotificationConsistencyIT.NotificationFaultConfiguration.class})
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("D-AT-19 通知一致性")
class NotificationConsistencyIT {

    @RegisterExtension
    static final DbTestSupport db = DbTestSupport.create("demo");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEMO_DATE = "2030-10-20";

    private static final UUID ACTIVITY_A = uuid(4001);
    private static final UUID VENUE_B = uuid(3102);
    private static final UUID EQUIPMENT_B = uuid(3202);
    private static final UUID STAFF_A = uuid(2006);

    @LocalServerPort
    private int port;

    @Autowired
    private NotificationDeliveryProbe notificationDeliveryProbe;

    private RestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("D-AT-19 / TC-19-1 首次消费失败恢复且重复消费不重发")
    void consumerFailureRecoversAndDuplicateDeliveryIsIdempotent() {
        String organizer = login("organizer_a");
        String staff = login("staff_a");

        assertEquals(0, totalNotifications(), "前置：当前 schema 内应无通知");

        // 业务提交（创建改期）与 outbox 同事务；随后注入的投递失败不得回滚已提交安排
        HttpResult create = createChange(ACTIVITY_A, 1, currentLockVersion(ACTIVITY_A), organizer);
        assertEquals(201, create.status(), "创建改期应成功：" + create.body());
        UUID changeId = UUID.fromString(create.body().path("data").path("id").asText());
        assertEquals("AWAITING_CONFIRMATIONS", changeStatus(changeId), "业务应已提交");
        Integer version = db.effectiveVersionNo(ACTIVITY_A);
        assertEquals(1, version == null ? -1 : version.intValue(), "通知失败不得回滚活动版本");

        // 轮询等待消费者首次失败后恢复并把事件处理完（禁止长 sleep）
        awaitSmall(() -> notDoneOutboxCount(ACTIVITY_A) == 0, 30_000);

        assertTrue(((FaultInjectingProbe) notificationDeliveryProbe).attempts() > 0,
                "通知投递未经过 NotificationDeliveryProbe：请 PM/CM 装配生产通知消费者");

        long total = totalNotifications();
        assertTrue(total > 0, "消费者恢复后应产生通知");
        assertEquals(total, distinctRecipients(), "每收件人恰 1 条通知（event_id+recipient_id 唯一）");
        assertNoDuplicateRecipients();
        assertEquals(1, db.notificationCount(STAFF_A), "工作人员 staff_a 应恰有 1 条改期通知");

        Long attempts = db.jdbc().queryForObject(
                "SELECT max(attempts) FROM outbox_event WHERE activity_id = ?", Long.class, ACTIVITY_A);
        assertTrue(attempts != null && attempts >= 2, "首次投递失败后应重试（attempts≥2），实际 " + attempts);

        // 模拟对同一 event 再次投递：消费者按 (event_id, recipient_id) 去重，不得新增通知
        long afterRecovery = totalNotifications();
        db.jdbc().update("UPDATE outbox_event SET status = 'PENDING', "
                        + "next_attempt_at = TIMESTAMPTZ '2000-01-01 00:00:00+00', last_error = NULL WHERE activity_id = ?",
                ACTIVITY_A);
        awaitSmall(() -> notDoneOutboxCount(ACTIVITY_A) == 0, 30_000);
        assertEquals(afterRecovery, totalNotifications(), "重复消费同一 event 不得新增通知");
        assertNoDuplicateRecipients();

        // 读取通知 ≠ 工作人员确认：read 只写 read_at，change_confirmation 不变
        UUID notificationId = db.jdbc().queryForObject(
                "SELECT id FROM notification WHERE recipient_id = ? AND activity_id = ?",
                UUID.class, STAFF_A, ACTIVITY_A);
        long confirmationsBefore = changeConfirmationCount(changeId);
        long pendingBefore = pendingConfirmationCount(changeId, STAFF_A);

        HttpResult read = call(HttpMethod.POST, "/api/v1/notifications/" + notificationId + "/read",
                staff, null, null);
        assertEquals(200, read.status(), "读取本人通知应成功：" + read.body());

        Timestamp readAt = db.jdbc().queryForObject(
                "SELECT read_at FROM notification WHERE id = ?", Timestamp.class, notificationId);
        assertNotNull(readAt, "读取通知后 read_at 应落库");
        assertEquals(confirmationsBefore, changeConfirmationCount(changeId), "读取通知不得改变工作人员确认记录");
        assertEquals(pendingBefore, pendingConfirmationCount(changeId, STAFF_A), "读取通知不得改变确认决定");
        assertEquals("AWAITING_CONFIRMATIONS", changeStatus(changeId), "读取通知不得推进改期状态");
    }

    // ---------------------------------------------------------------- 故障注入

    /**
     * 通知投递探针（仅测试使用，03 §2：失败注入只在测试上下文替换 bean/挂钩，不提供公网故障接口）。
     *
     * <p>装配依赖 PM/CM：生产通知消费者在投递某个 outbox 事件到某个收件人之前必须调用
     * {@link #beforeDelivery(UUID, UUID)}；本测试以 {@code @Primary} 覆盖为首次抛异常的探针。
     * 未完成该装配时本用例应报告阻塞，不得改为 Mock 后宣称通过。
     */
    public interface NotificationDeliveryProbe {
        void beforeDelivery(UUID eventId, UUID recipientId);
    }

    @TestConfiguration
    static class NotificationFaultConfiguration {
        @Bean
        @Primary
        NotificationDeliveryProbe notificationDeliveryProbe() {
            return new FaultInjectingProbe();
        }
    }

    static final class FaultInjectingProbe implements NotificationDeliveryProbe {
        private final AtomicInteger remainingFailures = new AtomicInteger(1);
        private final AtomicInteger attempts = new AtomicInteger();

        @Override
        public void beforeDelivery(UUID eventId, UUID recipientId) {
            attempts.incrementAndGet();
            if (remainingFailures.getAndDecrement() > 0) {
                throw new IllegalStateException("TC-19-1 注入故障：通知消费者首次投递失败");
            }
        }

        int attempts() {
            return attempts.get();
        }
    }

    // ---------------------------------------------------------------- HTTP

    private HttpResult createChange(UUID activityId, int baseVersionNo, int expectedLockVersion, String token) {
        ObjectNode plan = MAPPER.createObjectNode();
        plan.put("startAt", DEMO_DATE + "T15:00:00+08:00");
        plan.put("endAt", DEMO_DATE + "T16:00:00+08:00");
        plan.put("setupMinutes", 30);
        plan.put("teardownMinutes", 30);
        plan.put("venueId", VENUE_B.toString());
        ArrayNode equipment = plan.putArray("equipment");
        ObjectNode line = equipment.addObject();
        line.put("resourceId", EQUIPMENT_B.toString());
        line.put("quantity", 2);

        ObjectNode body = MAPPER.createObjectNode();
        body.put("baseVersionNo", baseVersionNo);
        body.set("targetPlan", plan);
        body.put("expectedLockVersion", expectedLockVersion);
        body.put("reason", "申请改期");
        return call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/changes",
                token, UUID.randomUUID().toString(), body);
    }

    private String login(String username) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("username", username);
        body.put("password", demoPassword());
        HttpResult res = call(HttpMethod.POST, "/api/v1/auth/login", null, null, body);
        assertEquals(200, res.status(), "登录失败 " + username + "：" + res.body());
        return res.body().path("data").path("token").asText();
    }

    private HttpResult call(HttpMethod method, String path, String token, String idemKey, JsonNode body) {
        RestClient.RequestBodySpec spec = client.method(method).uri(path);
        if (token != null) {
            spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        if (idemKey != null) {
            spec = spec.header("Idempotency-Key", idemKey);
        }
        if (body != null) {
            spec = spec.contentType(MediaType.APPLICATION_JSON).body(body.toString());
        }
        return spec.exchange((request, response) -> {
            InputStream in = response.getBody();
            byte[] bytes = in == null ? new byte[0] : in.readAllBytes();
            JsonNode node = bytes.length == 0 ? MAPPER.getNodeFactory().nullNode() : MAPPER.readTree(bytes);
            return new HttpResult(response.getStatusCode().value(), node);
        });
    }

    // ---------------------------------------------------------------- 数据库断言

    private void awaitSmall(BooleanSupplier condition, long timeoutMillis) {
        long deadline = System.nanoTime() + timeoutMillis * 1_000_000L;
        while (System.nanoTime() < deadline) {
            if (condition.getAsBoolean()) {
                return;
            }
            try {
                Thread.sleep(100);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new IllegalStateException("等待被中断", e);
            }
        }
        throw new AssertionError("等待条件在 " + timeoutMillis + "ms 内未达成");
    }

    private int currentLockVersion(UUID activityId) {
        Integer value = db.jdbc().queryForObject("SELECT lock_version FROM activity WHERE id = ?",
                Integer.class, activityId);
        return value == null ? 0 : value;
    }

    private String changeStatus(UUID changeId) {
        return db.jdbc().queryForObject("SELECT status FROM change_request WHERE id = ?", String.class, changeId);
    }

    private long totalNotifications() {
        Long count = db.jdbc().queryForObject("SELECT count(*) FROM notification", Long.class);
        return count == null ? 0 : count;
    }

    private long distinctRecipients() {
        Long count = db.jdbc().queryForObject("SELECT count(DISTINCT recipient_id) FROM notification", Long.class);
        return count == null ? 0 : count;
    }

    private void assertNoDuplicateRecipients() {
        Long duplicates = db.jdbc().queryForObject(
                "SELECT count(*) FROM (SELECT recipient_id FROM notification "
                        + "GROUP BY recipient_id HAVING count(*) > 1) d",
                Long.class);
        assertEquals(0, duplicates == null ? 0 : duplicates,
                "每收件人只应有 1 条通知（event_id+recipient_id 唯一）");
    }

    private long notDoneOutboxCount(UUID activityId) {
        Long count = db.jdbc().queryForObject(
                "SELECT count(*) FROM outbox_event WHERE activity_id = ? AND status <> 'DONE'",
                Long.class, activityId);
        return count == null ? 0 : count;
    }

    private long changeConfirmationCount(UUID changeId) {
        Long count = db.jdbc().queryForObject(
                "SELECT count(*) FROM change_confirmation WHERE change_id = ?", Long.class, changeId);
        return count == null ? 0 : count;
    }

    private long pendingConfirmationCount(UUID changeId, UUID userId) {
        Long count = db.jdbc().queryForObject(
                "SELECT count(*) FROM change_confirmation WHERE change_id = ? AND user_id = ? AND decision IS NULL",
                Long.class, changeId, userId);
        return count == null ? 0 : count;
    }

    private static String demoPassword() {
        String value = System.getenv("DEMO_PASSWORD");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 DEMO_PASSWORD");
        }
        return value;
    }

    private static UUID uuid(long tail) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", tail));
    }

    private record HttpResult(int status, JsonNode body) {
    }
}
