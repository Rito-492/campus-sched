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
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * D-AT-15 改期期间报名冻结（TC-15-1）与 D-AT-16 并发报名容量（TC-16-1/2/3）。
 *
 * <p>装载数据：{@code demo.json} + {@code scenarios/registration-concurrency.json}。活动 A=4001
 * （OPEN、registrationCapacity=20、student_a 已报名、staff_a 为工作人员）用于 TC-15-1；
 * 活动 4101（OPEN、registrationCapacity=1）与 20 名 perf_s01—perf_s20 用于 TC-16-1/2/3。
 * 固定 Clock=2030-10-19T00:00:00Z、DEMO_DATE=2030-10-20（03 §1）。
 *
 * <p>执行命令：{@code ./server/mvnw.cmd -f server/pom.xml "-Dit.test=RegistrationCapacityIT" verify}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("D-AT-15/16 报名冻结与并发容量")
class RegistrationCapacityIT {

    @RegisterExtension
    static final DbTestSupport db =
            DbTestSupport.create("demo", "scenarios/registration-concurrency");

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final String DEMO_DATE = "2030-10-20";

    private static final UUID ACTIVITY_A = uuid(4001);
    private static final UUID ACTIVITY_C = uuid(4101);
    private static final UUID VENUE_B = uuid(3102);
    private static final UUID EQUIPMENT_B = uuid(3202);
    private static final UUID STUDENT_A = uuid(2005);
    private static final UUID STUDENT_B = uuid(2009);
    private static final UUID STAFF_A = uuid(2006);

    @LocalServerPort
    private int port;

    private RestClient client;

    @BeforeEach
    void setUpClient() {
        client = RestClient.builder().baseUrl("http://localhost:" + port).build();
    }

    @Test
    @Order(1)
    @DisplayName("D-AT-15 / TC-15-1 待处理改期冻结报名，终态恢复")
    void pendingChangeFreezesRegistrationUntilTerminal() {
        String organizer = login("organizer_a");
        String studentAToken = login("student_a");
        String studentBToken = login("student_b");
        String staff = login("staff_a");

        // OPEN 活动 4001 创建待处理改期（staff_a 待确认 → AWAITING_CONFIRMATIONS）
        HttpResult create = createChange(ACTIVITY_A, 1, currentLockVersion(ACTIVITY_A), organizer);
        assertEquals(201, create.status(), "创建改期应成功：" + create.body());
        UUID changeId = UUID.fromString(create.body().path("data").path("id").asText());
        assertEquals("AWAITING_CONFIRMATIONS", changeStatus(changeId));

        // 冻结期：JOIN 与 WITHDRAW 均 409 CHANGE_PENDING
        HttpResult joinBlocked = registration(ACTIVITY_A, studentBToken, "JOIN", 1);
        assertEquals(409, joinBlocked.status(), "待处理改期期间 JOIN 应被拒：" + joinBlocked.body());
        assertEquals("CHANGE_PENDING", joinBlocked.body().path("error").path("code").asText());
        HttpResult withdrawBlocked = registration(ACTIVITY_A, studentAToken, "WITHDRAW", 1);
        assertEquals(409, withdrawBlocked.status(), "待处理改期期间 WITHDRAW 应被拒：" + withdrawBlocked.body());
        assertEquals("CHANGE_PENDING", withdrawBlocked.body().path("error").path("code").asText());

        // 冻结期 activity_registration 无变化
        assertEquals("ACTIVE", registrationStatus(ACTIVITY_A, STUDENT_A));
        assertNull(registrationStatus(ACTIVITY_A, STUDENT_B));
        assertEquals(1, activeRegistrationCount(ACTIVITY_A));

        // 改期到 REJECTED 终态
        ObjectNode reject = MAPPER.createObjectNode();
        reject.put("decision", "REJECT");
        reject.put("comment", "时间冲突");
        HttpResult rejected = call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/confirmation",
                staff, UUID.randomUUID().toString(), reject);
        assertEquals(200, rejected.status(), "工作人员拒绝应成功：" + rejected.body());
        assertEquals("REJECTED", changeStatus(changeId));

        // 终态后名额规则恢复：JOIN 成功落库
        HttpResult joinOk = registration(ACTIVITY_A, studentBToken, "JOIN", 1);
        assertEquals(200, joinOk.status(), "终态后 JOIN 应成功：" + joinOk.body());
        assertEquals("ACTIVE", joinOk.body().path("data").path("status").asText());
        assertEquals("ACTIVE", registrationStatus(ACTIVITY_A, STUDENT_B));
        assertEquals(2, activeRegistrationCount(ACTIVITY_A));
    }

    @Test
    @Order(2)
    @DisplayName("D-AT-16 / TC-16-1 容量1对20并发JOIN恰1成功")
    void concurrentJoinOnCapacityOneYieldsSingleActive() {
        List<String> tokens = new ArrayList<>();
        for (int i = 1; i <= 20; i++) {
            tokens.add(login(String.format("perf_s%02d", i)));
        }

        List<ConcurrentRunner.DbTask<HttpResult>> tasks = new ArrayList<>();
        for (String token : tokens) {
            tasks.add(conn -> registration(ACTIVITY_C, token, "JOIN", 1));
        }
        List<HttpResult> results = ConcurrentRunner.run(db.dataSource(), tasks);

        assertEquals(20, results.size());
        long ok = results.stream().filter(r -> r.status() == 200).count();
        long full = results.stream().filter(r -> r.status() == 409).count();
        assertEquals(1, ok, "20 并发 JOIN 应恰 1 个 200");
        assertEquals(19, full, "其余 19 个应为 409");
        for (HttpResult r : results) {
            if (r.status() == 409) {
                assertEquals("CAPACITY_FULL", r.body().path("error").path("code").asText(),
                        "409 应为 CAPACITY_FULL：" + r.body());
            }
        }
        assertEquals(1, db.activeReservationCount(ACTIVITY_C));
        assertEquals(1, activeRegistrationCount(ACTIVITY_C));
    }

    @Test
    @Order(3)
    @DisplayName("D-AT-16 / TC-16-2 中签者重复JOIN不增人数")
    void repeatedJoinByWinnerDoesNotIncreaseCount() {
        UUID winner = activeWinner();
        String token = login(usernameOf(winner));

        for (int i = 0; i < 2; i++) {
            HttpResult res = registration(ACTIVITY_C, token, "JOIN", 1);
            assertEquals(200, res.status(), "已中签学生重复 JOIN 应返回成功：" + res.body());
            assertEquals("ACTIVE", res.body().path("data").path("status").asText());
        }
        assertEquals(1, activeRegistrationCount(ACTIVITY_C), "重复 JOIN 不得增加 ACTIVE 人数");
    }

    @Test
    @Order(4)
    @DisplayName("D-AT-16 / TC-16-3 退出释放名额后他人JOIN成功")
    void withdrawReleasesCapacityForAnotherStudent() {
        UUID winner = activeWinner();
        String winnerToken = login(usernameOf(winner));

        HttpResult withdraw = registration(ACTIVITY_C, winnerToken, "WITHDRAW", 1);
        assertEquals(200, withdraw.status(), "中签学生退出应成功：" + withdraw.body());
        assertEquals("WITHDRAWN", registrationStatus(ACTIVITY_C, winner));
        assertEquals(0, activeRegistrationCount(ACTIVITY_C));

        UUID other = db.jdbc().queryForObject(
                "SELECT id FROM app_user WHERE username LIKE 'perf_s%' AND id <> ? ORDER BY username LIMIT 1",
                UUID.class, winner);
        String otherToken = login(usernameOf(other));
        HttpResult join = registration(ACTIVITY_C, otherToken, "JOIN", 1);
        assertEquals(200, join.status(), "名额释放后另一学生 JOIN 应成功：" + join.body());
        assertEquals(1, activeRegistrationCount(ACTIVITY_C));
    }

    // ---------------------------------------------------------------- HTTP

    private HttpResult registration(UUID activityId, String token, String action, int expectedVersionNo) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("action", action);
        body.put("expectedVersionNo", expectedVersionNo);
        return call(HttpMethod.PUT, "/api/v1/activities/" + activityId + "/registration",
                token, UUID.randomUUID().toString(), body);
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

    private long activeRegistrationCount(UUID activityId) {
        Long count = db.jdbc().queryForObject(
                "SELECT count(*) FROM activity_registration WHERE activity_id = ? AND status = 'ACTIVE'",
                Long.class, activityId);
        return count == null ? 0 : count;
    }

    private String registrationStatus(UUID activityId, UUID userId) {
        List<String> rows = db.jdbc().queryForList(
                "SELECT status FROM activity_registration WHERE activity_id = ? AND user_id = ?",
                String.class, activityId, userId);
        return rows.isEmpty() ? null : rows.get(0);
    }

    private UUID activeWinner() {
        return db.jdbc().queryForObject(
                "SELECT user_id FROM activity_registration WHERE activity_id = ? AND status = 'ACTIVE'",
                UUID.class, ACTIVITY_C);
    }

    private String usernameOf(UUID userId) {
        return db.jdbc().queryForObject("SELECT username FROM app_user WHERE id = ?", String.class, userId);
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
