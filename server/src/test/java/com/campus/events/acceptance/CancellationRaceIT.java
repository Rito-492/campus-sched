package com.campus.events.acceptance;

import com.campus.events.support.ConcurrentRunner;
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
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.web.client.RestClient;

import java.io.InputStream;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-AT-17 取消与竞争（TC-17-1 / TC-17-2）。
 *
 * <p>装载数据：{@code demo.json}。活动 A=4001（OPEN、version1，3101+3201×2、student_a 已报名，
 * staff_a 为工作人员）用于 TC-17-1；活动 B=4002（SCHEDULED、version1，3101+3201×1，无工作人员、
 * 无报名）用于 TC-17-2。固定 Clock=2030-10-19T00:00:00Z、DEMO_DATE=2030-10-20（03 §1）。
 *
 * <p>执行命令：{@code ./server/mvnw.cmd -f server/pom.xml "-Dit.test=CancellationRaceIT" verify}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("D-AT-17 取消与竞争")
class CancellationRaceIT {

    @RegisterExtension
    static final DbTestSupport db = DbTestSupport.create("demo");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEMO_DATE = "2030-10-20";

    private static final UUID ACTIVITY_A = uuid(4001);
    private static final UUID ACTIVITY_B = uuid(4002);
    private static final UUID VENUE_B = uuid(3102);
    private static final UUID EQUIPMENT_B = uuid(3202);
    private static final UUID STUDENT_A = uuid(2005);
    private static final UUID STAFF_A = uuid(2006);

    @LocalServerPort
    private int port;

    private RestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @DisplayName("D-AT-17 / TC-17-1 取消两次不多释放、不复活")
    void cancelTwiceDoesNotOverReleaseNorRevive() {
        String organizer = login("organizer_a");
        String staff = login("staff_a");

        // 先制造一个待处理改期：取消后必须作废，且其确认请求不得复活业务
        HttpResult create = createChange(ACTIVITY_A, 1, currentLockVersion(ACTIVITY_A), organizer);
        assertEquals(201, create.status(), "创建改期应成功：" + create.body());
        UUID changeId = UUID.fromString(create.body().path("data").path("id").asText());
        assertEquals("AWAITING_CONFIRMATIONS", changeStatus(changeId));

        int lock = currentLockVersion(ACTIVITY_A);
        String cancelKey = UUID.randomUUID().toString();
        HttpResult first = cancel(ACTIVITY_A, lock, cancelKey, organizer);
        assertEquals(200, first.status(), "首次取消应成功：" + first.body());
        assertEquals("CANCELLED", first.body().path("data").path("status").asText());

        long releasedAfterFirst = releasedReservationCount(ACTIVITY_A);
        long totalAfterFirst = totalReservationCount(ACTIVITY_A);

        assertEquals(0, db.activeReservationCount(ACTIVITY_A), "取消后不应残留 ACTIVE 预约");
        assertEquals("CANCELLED", db.activityStatus(ACTIVITY_A));
        assertEquals(0, pendingChangeCount(ACTIVITY_A), "取消后不应有有效待处理改期");
        assertEquals("CANCELLED_BY_ACTIVITY", changeStatus(changeId));
        assertEquals("CANCELLED", registrationStatus(ACTIVITY_A, STUDENT_A), "有效报名应转 CANCELLED");

        // 第二次：同 key 同 body 重放，返回首次结果且不得额外释放
        HttpResult replay = cancel(ACTIVITY_A, lock, cancelKey, organizer);
        assertEquals(200, replay.status(), "同 key 重放应返回首次成功结果：" + replay.body());
        assertEquals(releasedAfterFirst, releasedReservationCount(ACTIVITY_A), "重放不得额外释放预约");
        assertEquals(totalAfterFirst, totalReservationCount(ACTIVITY_A));

        // 第三次：不同 key，活动已 CANCELLED，应被拒绝且不得再次释放
        HttpResult other = cancel(ACTIVITY_A, currentLockVersion(ACTIVITY_A), UUID.randomUUID().toString(), organizer);
        assertEquals(409, other.status(), "已取消活动再次取消应 409：" + other.body());
        assertEquals(releasedAfterFirst, releasedReservationCount(ACTIVITY_A));
        assertEquals(totalAfterFirst, totalReservationCount(ACTIVITY_A));
        assertEquals("CANCELLED", db.activityStatus(ACTIVITY_A));

        // 旧请求不得复活：取消后的资源确认被拒，且不产生 ACTIVE 预约
        ObjectNode confirmBody = MAPPER.createObjectNode();
        confirmBody.put("expectedLockVersion", currentLockVersion(ACTIVITY_A));
        HttpResult confirm = call(HttpMethod.POST, "/api/v1/activities/" + ACTIVITY_A + "/resource-confirm",
                organizer, UUID.randomUUID().toString(), confirmBody);
        assertEquals(409, confirm.status(), "取消后资源确认应被拒：" + confirm.body());
        assertEquals(0, db.activeReservationCount(ACTIVITY_A));

        // 旧请求不得复活：被作废改期的确认被拒，确认记录不得改变
        long confirmationsBefore = changeConfirmationCount(changeId);
        long pendingConfirmationsBefore = pendingConfirmationCount(changeId, STAFF_A);
        ObjectNode decision = MAPPER.createObjectNode();
        decision.put("decision", "APPROVE");
        decision.put("comment", "");
        HttpResult staleConfirm = call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/confirmation",
                staff, UUID.randomUUID().toString(), decision);
        assertEquals(409, staleConfirm.status(), "已作废改期的确认应被拒：" + staleConfirm.body());
        assertEquals("CANCELLED_BY_ACTIVITY", changeStatus(changeId));
        assertEquals(confirmationsBefore, changeConfirmationCount(changeId));
        assertEquals(pendingConfirmationsBefore, pendingConfirmationCount(changeId, STAFF_A),
                "被拒的确认不得改写工作人员决定");
    }

    @Test
    @DisplayName("D-AT-17 / TC-17-2 取消与改期资源确认并发终态一致")
    void cancelRacesResourceDecisionToConsistentTerminalState() {
        String organizerB = login("organizer_b");
        String teacher = login("teacher_a");
        String resourceAdmin = login("resource_admin");

        // 让 4002 走到 PENDING_RESOURCE：无工作人员，创建后直接 PENDING_TEACHER
        HttpResult create = createChange(ACTIVITY_B, 1, currentLockVersion(ACTIVITY_B), organizerB);
        assertEquals(201, create.status(), "创建改期应成功：" + create.body());
        UUID changeId = UUID.fromString(create.body().path("data").path("id").asText());
        assertEquals("PENDING_TEACHER", changeStatus(changeId), "无工作人员时改期应直接 PENDING_TEACHER");

        ObjectNode approve = MAPPER.createObjectNode();
        approve.put("decision", "APPROVE");
        approve.put("comment", "");
        HttpResult teacherDecision = call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/teacher-decision",
                teacher, UUID.randomUUID().toString(), approve);
        assertEquals(200, teacherDecision.status(), "教师同意应成功：" + teacherDecision.body());
        assertEquals("PENDING_RESOURCE", changeStatus(changeId));

        int lock = currentLockVersion(ACTIVITY_B);
        String cancelKey = UUID.randomUUID().toString();
        List<ConcurrentRunner.DbTask<HttpResult>> tasks = List.of(
                conn -> cancel(ACTIVITY_B, lock, cancelKey, organizerB),
                conn -> call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/resource-decision",
                        resourceAdmin, UUID.randomUUID().toString(), approve));
        List<HttpResult> race = ConcurrentRunner.run(db.dataSource(), tasks);
        assertTrue(race.get(0).status() < 500, "取消分支不应返回 5xx：" + race.get(0).body());
        assertTrue(race.get(1).status() < 500, "资源确认分支不应返回 5xx：" + race.get(1).body());

        // 资源确认可能先提交，使取消拿到 STALE_VERSION；此时按当前版本重发取消，
        // 验证「取消始终可覆盖待处理改期」（01 §6），再断言联合终态。
        if (!"CANCELLED".equals(db.activityStatus(ACTIVITY_B))) {
            HttpResult retry = cancel(ACTIVITY_B, currentLockVersion(ACTIVITY_B),
                    UUID.randomUUID().toString(), organizerB);
            assertEquals(200, retry.status(), "资源确认先提交后取消仍应成功：" + retry.body());
        }

        assertEquals("CANCELLED", db.activityStatus(ACTIVITY_B), "无论竞争顺序最终都应为 CANCELLED");
        assertEquals(0, db.activeReservationCount(ACTIVITY_B), "不应残留任何 ACTIVE 预约");
        assertEquals(0, pendingChangeCount(ACTIVITY_B), "不应残留待处理改期");
        assertTrue(List.of("APPLIED", "CANCELLED_BY_ACTIVITY").contains(changeStatus(changeId)),
                "改期应为终态 APPLIED 或 CANCELLED_BY_ACTIVITY，实际 " + changeStatus(changeId));
    }

    // ---------------------------------------------------------------- HTTP

    private HttpResult cancel(UUID activityId, int expectedLockVersion, String idemKey, String token) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("reason", "取消");
        body.put("expectedLockVersion", expectedLockVersion);
        return call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/cancel", token, idemKey, body);
    }

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

    private int currentLockVersion(UUID activityId) {
        Integer value = db.jdbc().queryForObject("SELECT lock_version FROM activity WHERE id = ?",
                Integer.class, activityId);
        return value == null ? 0 : value;
    }

    private String changeStatus(UUID changeId) {
        return db.jdbc().queryForObject("SELECT status FROM change_request WHERE id = ?", String.class, changeId);
    }

    private long pendingChangeCount(UUID activityId) {
        Long count = db.jdbc().queryForObject(
                "SELECT count(*) FROM change_request WHERE activity_id = ? "
                        + "AND status IN ('AWAITING_CONFIRMATIONS','PENDING_TEACHER','PENDING_RESOURCE')",
                Long.class, activityId);
        return count == null ? 0 : count;
    }

    private long releasedReservationCount(UUID activityId) {
        Long count = db.jdbc().queryForObject(
                "SELECT count(*) FROM reservation WHERE activity_id = ? AND status = 'RELEASED'",
                Long.class, activityId);
        return count == null ? 0 : count;
    }

    private long totalReservationCount(UUID activityId) {
        Long count = db.jdbc().queryForObject(
                "SELECT count(*) FROM reservation WHERE activity_id = ?", Long.class, activityId);
        return count == null ? 0 : count;
    }

    private String registrationStatus(UUID activityId, UUID userId) {
        List<String> rows = db.jdbc().queryForList(
                "SELECT status FROM activity_registration WHERE activity_id = ? AND user_id = ?",
                String.class, activityId, userId);
        return rows.isEmpty() ? null : rows.get(0);
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
