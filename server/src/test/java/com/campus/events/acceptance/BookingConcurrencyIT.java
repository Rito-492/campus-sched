package com.campus.events.acceptance;

import com.campus.events.support.ConcurrentRunner;
import com.campus.events.support.DbTestSupport;
import com.campus.events.support.TestClockConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.test.annotation.DirtiesContext;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-AT-07 并发资源确认集成验收测试（Booking）。
 *
 * <p>对应设计：{@code docs/testing/test-design.md} 的 TC-07-1。断言依据 02 数据与接口契约
 * （resource-confirm 冲突 details 固定为 {@code {conflicts:[]}}）与 01 开发基线（首次确认失败不得产生部分占用）。
 *
 * <p>装载数据：{@code test-data/demo.json}（固定 Clock=2030-10-19T00:00:00Z、DEMO_DATE=2030-10-20）。
 * 演示活动 A 占用 3101 的 [09:30,11:30)、B 占用 3101 的 [14:30,16:30)，故本测试把两个新活动的
 * 竞争时段放在 3101 的 12:00—13:00（布撤场 0，对 A/B 均无重叠），使唯一竞争只发生在 X、Y 之间。
 *
 * <p>执行命令（在仓库根目录）：
 * {@code ./server/mvnw.cmd -f server/pom.xml "-Dit.test=BookingConcurrencyIT" verify}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("D-AT-07 两活动抢同一资源恰一成功（BookingConcurrencyIT）")
class BookingConcurrencyIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final UUID USER_TEACHER_A = UUID.fromString("00000000-0000-0000-0000-000000002003");
    private static final UUID USER_RESOURCE_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000002004");

    private static final UUID VENUE_3101 = UUID.fromString("00000000-0000-0000-0000-000000003101");
    private static final UUID EQUIP_3201 = UUID.fromString("00000000-0000-0000-0000-000000003201");

    private static final String START_AT = "2030-10-20T12:00:00+08:00";
    private static final String END_AT = "2030-10-20T13:00:00+08:00";
    private static final int EQUIPMENT_QUANTITY = 2;

    @RegisterExtension
    static final DbTestSupport db = DbTestSupport.create("demo");

    @Autowired
    private TestRestTemplate rest;

    @Test
    @DisplayName("D-AT-07 / TC-07-1 同步确认同场地同器材，恰 1 成功 1 RESOURCE_CONFLICT")
    void tc0701_concurrentResourceConfirmExactlyOneWins() {
        String organizerA = login("organizer_a");
        String organizerB = login("organizer_b");
        String resource = login("resource_admin");

        String activityX = createApprovedUnreserved(organizerA, "并发确认活动X（测试数据）");
        String activityY = createApprovedUnreserved(organizerB, "并发确认活动Y（测试数据）");
        int lockX = readActivityLockVersion(organizerA, activityX);
        int lockY = readActivityLockVersion(organizerB, activityY);

        List<ConcurrentRunner.DbTask<HttpResult>> tasks = new ArrayList<>();
        tasks.add(conn -> confirmActivity(activityX, lockX, resource));
        tasks.add(conn -> confirmActivity(activityY, lockY, resource));
        List<HttpResult> results = ConcurrentRunner.run(db.dataSource(), tasks);

        HttpResult resultX = results.get(0);
        HttpResult resultY = results.get(1);
        assertEquals(1L, results.stream().filter(r -> r.status() == 200).count(),
                () -> "应恰有 1 个 200：" + results);
        assertEquals(1L, results.stream().filter(r -> r.status() == 409).count(),
                () -> "应恰有 1 个 409：" + results);

        boolean xWon = resultX.status() == 200;
        String winnerId = xWon ? activityX : activityY;
        String loserId = xWon ? activityY : activityX;
        HttpResult conflictResult = xWon ? resultY : resultX;

        assertEquals("RESOURCE_CONFLICT", conflictResult.errorCode());
        JsonNode details = conflictResult.error().path("details");
        assertTrue(details.has("conflicts"), "冲突 details 必须含 conflicts 数组");
        assertTrue(details.path("conflicts").isArray());

        assertEquals("SCHEDULED", db.activityStatus(UUID.fromString(winnerId)));
        assertNotNull(db.effectiveVersionNo(UUID.fromString(winnerId)));
        assertEquals(2L, db.activeReservationCount(UUID.fromString(winnerId)),
                "成功活动应有 场地1 + 器材1 = 2 条 ACTIVE 预约");

        assertEquals("APPROVED_UNRESERVED", db.activityStatus(UUID.fromString(loserId)),
                "失败活动状态应保留 APPROVED_UNRESERVED");
        assertEquals(0L, db.activeReservationCount(UUID.fromString(loserId)),
                "失败活动不得产生任何部分占用");

        Instant from = OffsetDateTime.parse(START_AT).toInstant();
        Instant to = OffsetDateTime.parse(END_AT).toInstant();
        long equipmentUsage = db.sumActiveQuantity(EQUIP_3201, from, to);
        assertEquals(2L, equipmentUsage, "3201 在竞争区间只应有 1 个活动的 2 件 ACTIVE");
        assertTrue(equipmentUsage <= 2, "3201 并发占用不得超过库存 2");
    }

    private HttpResult confirmActivity(String activityId, int lockVersion, String resourceToken) {
        return call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/resource-confirm",
                resourceToken, idem(), toJson(versionCommand(lockVersion)));
    }

    private String createApprovedUnreserved(String ownerToken, String title) {
        ObjectNode input = activityInput(title, 10, 5, List.of(),
                planNode(START_AT, END_AT, 0, 0, VENUE_3101, EQUIP_3201, EQUIPMENT_QUANTITY));
        HttpResult created = call(HttpMethod.POST, "/api/v1/activities", ownerToken, idem(), toJson(input));
        assertEquals(201, created.status(), () -> "创建活动失败：" + created.body());
        String activityId = created.data().path("id").asText();
        int lockVersion = created.data().path("lockVersion").asInt();

        HttpResult submitted = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/submit",
                ownerToken, idem(), toJson(versionCommand(lockVersion)));
        assertEquals(200, submitted.status(), () -> "提交活动失败：" + submitted.body());

        String teacher = login("teacher_a");
        HttpResult approved = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/teacher-decision",
                teacher, idem(),
                toJson(decisionBody("APPROVE", "", submitted.data().path("lockVersion").asInt())));
        assertEquals(200, approved.status(), () -> "教师审批失败：" + approved.body());
        assertEquals("APPROVED_UNRESERVED", approved.data().path("status").asText());
        return activityId;
    }

    private int readActivityLockVersion(String token, String activityId) {
        HttpResult r = call(HttpMethod.GET, "/api/v1/activities/" + activityId, token, null, null);
        assertEquals(200, r.status(), () -> "读取活动失败：" + r.body());
        return r.data().path("lockVersion").asInt();
    }

    private HttpResult call(HttpMethod method, String path, String token, String idempotencyKey, String jsonBody) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        if (token != null) {
            headers.setBearerAuth(token);
        }
        if (idempotencyKey != null) {
            headers.set("Idempotency-Key", idempotencyKey);
        }
        HttpEntity<String> entity = new HttpEntity<>(jsonBody, headers);
        ResponseEntity<String> response = rest.exchange(path, method, entity, String.class);
        return new HttpResult(response.getStatusCode().value(), parse(response.getBody()));
    }

    private String login(String username) {
        HttpResult r = call(HttpMethod.POST, "/api/v1/auth/login", null, null,
                toJson(loginBody(username, demoPassword())));
        assertEquals(200, r.status(), () -> username + " 登录失败：" + r.body());
        return r.data().path("token").asText();
    }

    private static String demoPassword() {
        String value = System.getenv("DEMO_PASSWORD");
        assertNotNull(value, "缺少 DEMO_PASSWORD 环境变量（03 §1/§3）");
        assertFalse(value.isBlank(), "DEMO_PASSWORD 不能为空");
        return value;
    }

    private static String idem() {
        return UUID.randomUUID().toString();
    }

    private static ObjectNode loginBody(String username, String password) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("username", username);
        node.put("password", password);
        return node;
    }

    private static ObjectNode versionCommand(int expectedLockVersion) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("expectedLockVersion", expectedLockVersion);
        return node;
    }

    private static ObjectNode decisionBody(String decision, String comment, int expectedLockVersion) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("decision", decision);
        node.put("comment", comment);
        node.put("expectedLockVersion", expectedLockVersion);
        return node;
    }

    private static ObjectNode activityInput(String title, int expectedAttendees, int registrationCapacity,
                                            List<UUID> staffUserIds, ObjectNode plan) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("title", title);
        node.put("description", title + "（测试数据）");
        node.put("expectedAttendees", expectedAttendees);
        node.put("registrationCapacity", registrationCapacity);
        ArrayNode staff = node.putArray("staffUserIds");
        for (UUID id : staffUserIds) {
            staff.add(id.toString());
        }
        node.set("plan", plan);
        return node;
    }

    private static ObjectNode planNode(String startAt, String endAt, int setupMinutes, int teardownMinutes,
                                       UUID venueId, UUID equipmentId, int equipmentQuantity) {
        ObjectNode plan = MAPPER.createObjectNode();
        plan.put("startAt", startAt);
        plan.put("endAt", endAt);
        plan.put("setupMinutes", setupMinutes);
        plan.put("teardownMinutes", teardownMinutes);
        plan.put("venueId", venueId.toString());
        ArrayNode equipment = plan.putArray("equipment");
        if (equipmentId != null) {
            ObjectNode line = equipment.addObject();
            line.put("resourceId", equipmentId.toString());
            line.put("quantity", equipmentQuantity);
        }
        return plan;
    }

    private static String toJson(ObjectNode node) {
        try {
            return MAPPER.writeValueAsString(node);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("JSON 序列化失败", e);
        }
    }

    private static JsonNode parse(String body) {
        if (body == null || body.isBlank()) {
            return NullNode.getInstance();
        }
        try {
            return MAPPER.readTree(body);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("响应不是合法 JSON：" + body, e);
        }
    }

    private record HttpResult(int status, JsonNode body) {
        JsonNode data() {
            return body.path("data");
        }

        JsonNode error() {
            return body.path("error");
        }

        String errorCode() {
            return body.path("error").path("code").asText();
        }
    }
}
