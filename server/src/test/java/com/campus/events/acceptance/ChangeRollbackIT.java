package com.campus.events.acceptance;

import com.campus.events.contract.Plan;
import com.campus.events.contract.SchedulingPort;
import com.campus.events.support.DbTestSupport;
import com.campus.events.support.TestClockConfiguration;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.NullNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
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
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.reset;

/**
 * D-AT-11 / D-AT-12 / D-AT-13 / D-AT-14 改期拒绝、抢占、自身排除与事务回滚集成验收测试（ChangeRollback）。
 *
 * <p>对应设计：{@code docs/testing/test-design.md} 的 TC-11-1/2、TC-12-1、TC-13-1、TC-14-1。
 * 断言依据 02 数据与接口契约（改期状态机、409 details 结构 {@code {conflicts,change}}）与
 * 01 开发基线（拒绝/冲突保留原安排；activatePlan 之后 replaceReservations 注入异常必须整体回滚）。
 *
 * <p>装载数据：{@code test-data/demo.json}（固定 Clock=2030-10-19T00:00:00Z、DEMO_DATE=2030-10-20）。
 * 方法按 {@link Order} 顺序执行：拒绝与冲突测试都不改变 A 的有效安排，最后一例才把 A 推进到 version2，
 * 从而让 TC-13 的「自身旧占用排除」始终针对 A 的原始 [09:30,11:30) 占用。
 *
 * <p>TC-14 通过 {@link MockitoSpyBean} 在测试上下文包装 {@link SchedulingPort}，不改生产代码；
 * 该挂钩依赖 PM/DS 在 M0 按 02 §5 装配可注入的 SchedulingPort bean（beanname/实现由数据侧提供）。
 *
 * <p>执行命令（在仓库根目录）：
 * {@code ./server/mvnw.cmd -f server/pom.xml "-Dit.test=ChangeRollbackIT" verify}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
@DisplayName("D-AT-11/12/13/14 改期拒绝、抢占、自身排除与回滚（ChangeRollbackIT）")
class ChangeRollbackIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final UUID USER_STAFF_A = UUID.fromString("00000000-0000-0000-0000-000000002006");
    private static final UUID USER_STUDENT_B = UUID.fromString("00000000-0000-0000-0000-000000002009");

    private static final UUID ACTIVITY_A = UUID.fromString("00000000-0000-0000-0000-000000004001");

    private static final UUID VENUE_3101 = UUID.fromString("00000000-0000-0000-0000-000000003101");
    private static final UUID VENUE_3102 = UUID.fromString("00000000-0000-0000-0000-000000003102");
    private static final UUID EQUIP_3201 = UUID.fromString("00000000-0000-0000-0000-000000003201");
    private static final UUID EQUIP_3202 = UUID.fromString("00000000-0000-0000-0000-000000003202");

    @RegisterExtension
    static final DbTestSupport db = DbTestSupport.create("demo");

    @Autowired
    private TestRestTemplate rest;

    /** D-AT-14 故障注入挂钩；依赖 M0 按 02 §5 装配的 SchedulingPort bean。 */
    @MockitoSpyBean
    private SchedulingPort schedulingPort;

    // ------------------------------------------------------------------ D-AT-11

    @Test
    @Order(1)
    @DisplayName("D-AT-11 / TC-11-1 工作人员拒绝改期，A 原安排保留且报名解冻")
    void tc1101_staffRejectKeepsOriginalPlan() {
        String owner = login("organizer_a");
        String staff = login("staff_a");
        int version = readActivityVersion(owner, ACTIVITY_A.toString());
        int lockVersion = readActivityLockVersion(owner, ACTIVITY_A.toString());
        String changeId = createPendingChange(owner, version, lockVersion,
                "2030-10-20T18:00:00+08:00", "2030-10-20T19:00:00+08:00", 30, 30,
                VENUE_3102, EQUIP_3202, 2);
        assertEquals("AWAITING_CONFIRMATIONS", getChangeStatus(owner, changeId));

        Integer effectiveBefore = db.effectiveVersionNo(ACTIVITY_A);
        long activeBefore = db.activeReservationCount(ACTIVITY_A);
        long versionsBefore = countWhere("SELECT count(*) FROM activity_version WHERE activity_id = ?", ACTIVITY_A);

        HttpResult rejected = confirmChange(staff, changeId, "REJECT", "时间冲突（测试数据）");
        assertEquals(200, rejected.status(), () -> "工作人员拒绝失败：" + rejected.body());
        assertEquals("REJECTED", rejected.data().path("status").asText());
        assertEquals("REJECTED", getChangeStatus(owner, changeId));
        assertEquals("REJECT", queryString(
                "SELECT decision FROM change_confirmation WHERE change_id = ? AND user_id = ?",
                UUID.fromString(changeId), USER_STAFF_A));

        assertEquals(effectiveBefore, db.effectiveVersionNo(ACTIVITY_A), "effective_version_no 必须保留");
        assertEquals(activeBefore, db.activeReservationCount(ACTIVITY_A), "旧 ACTIVE 预约必须保留");
        assertEquals(versionsBefore, countWhere(
                "SELECT count(*) FROM activity_version WHERE activity_id = ?", ACTIVITY_A), "不得新增活动版本");

        // 终态解除报名冻结：随后 JOIN 不再 CHANGE_PENDING
        String studentB = login("student_b");
        ObjectNode join = MAPPER.createObjectNode();
        join.put("action", "JOIN");
        join.put("expectedVersionNo", db.effectiveVersionNo(ACTIVITY_A));
        HttpResult joined = call(HttpMethod.PUT, "/api/v1/activities/" + ACTIVITY_A + "/registration",
                studentB, idem(), toJson(join));
        assertEquals(200, joined.status(), () -> "终态后报名应恢复：" + joined.body());
        assertNotEquals("CHANGE_PENDING", joined.errorCode());
    }

    @Test
    @Order(2)
    @DisplayName("D-AT-11 / TC-11-2 教师驳回 PENDING_TEACHER 改期，A 原安排保留")
    void tc1102_teacherRejectKeepsOriginalPlan() {
        String owner = login("organizer_a");
        String staff = login("staff_a");
        String teacher = login("teacher_a");
        int version = readActivityVersion(owner, ACTIVITY_A.toString());
        int lockVersion = readActivityLockVersion(owner, ACTIVITY_A.toString());
        String changeId = createPendingChange(owner, version, lockVersion,
                "2030-10-20T16:00:00+08:00", "2030-10-20T17:00:00+08:00", 30, 30,
                VENUE_3102, EQUIP_3202, 2);

        HttpResult confirmed = confirmChange(staff, changeId, "APPROVE", "");
        assertEquals(200, confirmed.status(), () -> "工作人员同意失败：" + confirmed.body());
        assertEquals("PENDING_TEACHER", getChangeStatus(owner, changeId));

        Integer effectiveBefore = db.effectiveVersionNo(ACTIVITY_A);
        long activeBefore = db.activeReservationCount(ACTIVITY_A);
        long versionsBefore = countWhere("SELECT count(*) FROM activity_version WHERE activity_id = ?", ACTIVITY_A);

        HttpResult rejected = teacherDecisionOnChange(teacher, changeId, "REJECT", "不同意（测试数据）");
        assertEquals(200, rejected.status(), () -> "教师驳回失败：" + rejected.body());
        assertEquals("REJECTED", rejected.data().path("status").asText());
        assertEquals("REJECTED", getChangeStatus(owner, changeId));
        assertNotNull(queryString("SELECT teacher_decision_json::text FROM change_request WHERE id = ?",
                UUID.fromString(changeId)), "teacher_decision_json 应落库");

        assertEquals(effectiveBefore, db.effectiveVersionNo(ACTIVITY_A), "活动版本指针不得变化");
        assertEquals(activeBefore, db.activeReservationCount(ACTIVITY_A), "旧 ACTIVE 预约必须保留");
        assertEquals(versionsBefore, countWhere(
                "SELECT count(*) FROM activity_version WHERE activity_id = ?", ACTIVITY_A), "不得新增活动版本");
    }

    // ------------------------------------------------------------------ D-AT-12

    @Test
    @Order(3)
    @DisplayName("D-AT-12 / TC-12-1 预览可行后被抢占，最终确认 CONFLICTED 且原安排保留")
    void tc1201_finalConfirmationConflictedWhenPreempted() {
        String owner = login("organizer_a");
        String staff = login("staff_a");
        String teacher = login("teacher_a");
        String resource = login("resource_admin");
        String organizerB = login("organizer_b");

        int version = readActivityVersion(owner, ACTIVITY_A.toString());
        int lockVersion = readActivityLockVersion(owner, ACTIVITY_A.toString());
        String changeId = createPendingChange(owner, version, lockVersion,
                "2030-10-20T15:00:00+08:00", "2030-10-20T16:00:00+08:00", 30, 30,
                VENUE_3102, EQUIP_3202, 2);

        assertEquals(200, confirmChange(staff, changeId, "APPROVE", "").status());
        assertEquals(200, teacherDecisionOnChange(teacher, changeId, "APPROVE", "").status());
        assertEquals("PENDING_RESOURCE", getChangeStatus(owner, changeId));

        Integer effectiveBefore = db.effectiveVersionNo(ACTIVITY_A);
        long activeBefore = db.activeReservationCount(ACTIVITY_A);
        long versionsBefore = countWhere("SELECT count(*) FROM activity_version WHERE activity_id = ?", ACTIVITY_A);

        // 另一活动 Z 先占用候选资源 3102 + 3202×2（与改期目标完全重叠）
        String zId = createApprovedUnreserved(organizerB, "抢占候选资源活动Z（测试数据）",
                "2030-10-20T15:00:00+08:00", "2030-10-20T16:00:00+08:00", 30, 30,
                VENUE_3102, EQUIP_3202, 2, 30, 20);
        HttpResult zConfirmed = call(HttpMethod.POST, "/api/v1/activities/" + zId + "/resource-confirm",
                resource, idem(), toJson(versionCommand(readActivityLockVersion(organizerB, zId))));
        assertEquals(200, zConfirmed.status(), () -> "Z 抢占资源失败：" + zConfirmed.body());

        HttpResult conflicted = resourceDecisionOnChange(resource, changeId, "APPROVE", idem(), "");
        assertEquals(409, conflicted.status(), () -> "应报资源冲突：" + conflicted.body());
        assertEquals("RESOURCE_CONFLICT", conflicted.errorCode());
        JsonNode details = conflicted.error().path("details");
        assertTrue(details.has("conflicts"), "details 必须含 conflicts");
        assertTrue(details.has("change"), "details 必须含 change");
        assertEquals("CONFLICTED", details.path("change").path("status").asText());
        assertEquals("CONFLICTED", getChangeStatus(owner, changeId));

        assertEquals(effectiveBefore, db.effectiveVersionNo(ACTIVITY_A), "A 原有效安排必须保留");
        assertEquals(activeBefore, db.activeReservationCount(ACTIVITY_A), "无新 ACTIVE 预约，旧预约仍 ACTIVE");
        assertEquals(versionsBefore, countWhere(
                "SELECT count(*) FROM activity_version WHERE activity_id = ?", ACTIVITY_A), "不得新增活动版本");
    }

    // ------------------------------------------------------------------ D-AT-13

    @Test
    @Order(4)
    @DisplayName("D-AT-13 / TC-13-1 目标与自身旧占用重叠但排除自身后不误报")
    void tc1301_schedulePreviewExcludesSelf() {
        String owner = login("organizer_a");
        int baseVersion = readActivityVersion(owner, ACTIVITY_A.toString());
        long activeBefore = db.activeReservationCount(ACTIVITY_A);
        long changesBefore = countWhere("SELECT count(*) FROM change_request WHERE activity_id = ?", ACTIVITY_A);

        // A 原占用 [09:30,11:30)；目标 10:30—11:30 布撤场 30 → 占用 [10:00,12:00)，与自身旧占用部分重叠
        ObjectNode body = MAPPER.createObjectNode();
        body.put("baseVersionNo", baseVersion);
        body.set("targetPlan", planNode("2030-10-20T10:30:00+08:00", "2030-10-20T11:30:00+08:00",
                30, 30, VENUE_3101, EQUIP_3201, 2));
        HttpResult preview = call(HttpMethod.POST, "/api/v1/activities/" + ACTIVITY_A + "/schedule-preview",
                owner, null, toJson(body));
        assertEquals(200, preview.status(), () -> "预览失败：" + preview.body());
        assertTrue(preview.data().path("feasible").asBoolean(), "排除自身旧占用后应可行");
        JsonNode conflicts = preview.data().path("conflicts");
        assertEquals(0, conflicts.size(), "不得把自身旧占用算作冲突：" + conflicts);
        for (JsonNode conflict : conflicts) {
            assertNotEquals("VENUE_OCCUPIED", conflict.path("code").asText(), "不得误报 VENUE_OCCUPIED");
        }

        // 预览只读：不写预约、不建改期
        assertEquals(activeBefore, db.activeReservationCount(ACTIVITY_A), "预览不得修改预约");
        assertEquals(changesBefore, countWhere(
                "SELECT count(*) FROM change_request WHERE activity_id = ?", ACTIVITY_A), "预览不得新建改期");
    }

    // ------------------------------------------------------------------ D-AT-14

    @Test
    @Order(5)
    @DisplayName("D-AT-14 / TC-14-1 replaceReservations 注入异常整体回滚，原 key 重试可继续")
    void tc1401_replaceReservationsFailureRollsBack() {
        String owner = login("organizer_a");
        String staff = login("staff_a");
        String teacher = login("teacher_a");
        String resource = login("resource_admin");

        int version = readActivityVersion(owner, ACTIVITY_A.toString());
        int lockVersion = readActivityLockVersion(owner, ACTIVITY_A.toString());
        String changeId = createPendingChange(owner, version, lockVersion,
                "2030-10-20T18:00:00+08:00", "2030-10-20T19:00:00+08:00", 30, 30,
                VENUE_3102, EQUIP_3202, 2);
        assertEquals(200, confirmChange(staff, changeId, "APPROVE", "").status());
        assertEquals(200, teacherDecisionOnChange(teacher, changeId, "APPROVE", "").status());
        assertEquals("PENDING_RESOURCE", getChangeStatus(owner, changeId));

        Integer beforeEffectiveVersion = db.effectiveVersionNo(ACTIVITY_A);
        long beforeActiveReservations = db.activeReservationCount(ACTIVITY_A);
        long beforeOutboxCount = db.outboxCount();

        String key = idem();
        ObjectNode decision = MAPPER.createObjectNode();
        decision.put("decision", "APPROVE");
        decision.put("comment", "");

        doThrow(new RuntimeException("测试故障注入：replaceReservations 失败（D-AT-14）"))
                .when(schedulingPort).replaceReservations(any(UUID.class), anyInt(), any(Plan.class));
        try {
            HttpResult failed = call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/resource-decision",
                    resource, key, toJson(decision));
            assertEquals(500, failed.status(), () -> "注入故障后应返回 500：" + failed.body());
            assertEquals("INTERNAL_ERROR", failed.errorCode());
        } finally {
            reset(schedulingPort);
        }

        assertEquals(beforeEffectiveVersion, db.effectiveVersionNo(ACTIVITY_A), "活动版本指针必须回滚");
        assertEquals(beforeActiveReservations, db.activeReservationCount(ACTIVITY_A), "预约必须回滚");
        assertEquals(beforeOutboxCount, db.outboxCount(), "outbox 必须回滚");
        assertEquals("PENDING_RESOURCE", getChangeStatus(owner, changeId), "改期应保留原待处理状态");

        // 移除故障后以原 Idempotency-Key 重试可继续
        HttpResult retried = call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/resource-decision",
                resource, key, toJson(decision));
        assertEquals(200, retried.status(), () -> "重试失败：" + retried.body());
        assertEquals("APPLIED", retried.data().path("status").asText());
        assertEquals(Integer.valueOf(2), db.effectiveVersionNo(ACTIVITY_A), "成功后应切到 version2");
        assertTrue(db.activeReservationCount(ACTIVITY_A) > 0, "成功后应有新 ACTIVE 预约");
    }

    // ------------------------------------------------------------------ 支撑

    private String createPendingChange(String ownerToken, int baseVersionNo, int expectedLockVersion,
                                       String startAt, String endAt, int setupMinutes, int teardownMinutes,
                                       UUID venueId, UUID equipmentId, int equipmentQuantity) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("baseVersionNo", baseVersionNo);
        body.set("targetPlan", planNode(startAt, endAt, setupMinutes, teardownMinutes,
                venueId, equipmentId, equipmentQuantity));
        body.put("expectedLockVersion", expectedLockVersion);
        body.put("reason", "改期到更合适时段（测试数据）");
        HttpResult r = call(HttpMethod.POST, "/api/v1/activities/" + ACTIVITY_A + "/changes",
                ownerToken, idem(), toJson(body));
        assertEquals(201, r.status(), () -> "创建改期失败：" + r.body());
        return r.data().path("id").asText();
    }

    private HttpResult confirmChange(String token, String changeId, String decision, String comment) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("decision", decision);
        body.put("comment", comment);
        return call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/confirmation", token, idem(), toJson(body));
    }

    private HttpResult teacherDecisionOnChange(String token, String changeId, String decision, String comment) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("decision", decision);
        body.put("comment", comment);
        return call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/teacher-decision",
                token, idem(), toJson(body));
    }

    private HttpResult resourceDecisionOnChange(String token, String changeId, String decision,
                                                String idempotencyKey, String comment) {
        ObjectNode body = MAPPER.createObjectNode();
        body.put("decision", decision);
        body.put("comment", comment);
        return call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/resource-decision",
                token, idempotencyKey, toJson(body));
    }

    private String getChangeStatus(String token, String changeId) {
        HttpResult r = call(HttpMethod.GET, "/api/v1/changes/" + changeId, token, null, null);
        assertEquals(200, r.status(), () -> "读取改期失败：" + r.body());
        return r.data().path("status").asText();
    }

    private String createApprovedUnreserved(String ownerToken, String title, String startAt, String endAt,
                                            int setupMinutes, int teardownMinutes, UUID venueId,
                                            UUID equipmentId, int equipmentQuantity,
                                            int expectedAttendees, int registrationCapacity) {
        ObjectNode input = activityInput(title, expectedAttendees, registrationCapacity, List.of(),
                planNode(startAt, endAt, setupMinutes, teardownMinutes, venueId, equipmentId, equipmentQuantity));
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
        return readActivityFieldInt(token, activityId, "lockVersion");
    }

    private int readActivityVersion(String token, String activityId) {
        return readActivityFieldInt(token, activityId, "versionNo");
    }

    private int readActivityFieldInt(String token, String activityId, String field) {
        HttpResult r = call(HttpMethod.GET, "/api/v1/activities/" + activityId, token, null, null);
        assertEquals(200, r.status(), () -> "读取活动失败：" + r.body());
        return r.data().path(field).asInt();
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

    private long countWhere(String sql, Object... args) {
        Long value = db.jdbc().queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private String queryString(String sql, Object... args) {
        return db.jdbc().queryForObject(sql, String.class, args);
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
