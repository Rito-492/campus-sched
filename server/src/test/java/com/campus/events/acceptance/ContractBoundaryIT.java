package com.campus.events.acceptance;

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

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 契约边界回归（QA-05 任务5：真实 HTTP + 真实 PostgreSQL，无替身）。
 *
 * <p>依据（预期全部取自公共契约，不把已知缺陷行为写成预期）：
 * <ul>
 *   <li>02 契约 §附加校验：title 长度 1—100、原因/意见 ≤1000、拒绝理由至少 1 字、意见允许空字符串；</li>
 *   <li>01-基线 §3：活动 30 分钟～6 小时，布撤场 0～120 分钟；</li>
 *   <li>01-基线 §2/N-AT-02：资源资格按活动所有者身份校验，受限申请被拒（403 RESOURCE_INELIGIBLE）且无预约；</li>
 *   <li>BR-07/BR-23、01-基线 §3：所有者不需要向自己确认；无其他工作人员的改期直接复审。</li>
 * </ul>
 *
 * <p>覆盖用例：
 * <ul>
 *   <li>{@link #titleBoundary_1and100Accepted_0and101Rejected}：标题 1/100 字接受，0/101 字 400 VALIDATION_ERROR。</li>
 *   <li>{@link #durationBoundary_30minAnd6hAccepted_29minAnd6h1mRejected}：时长 30 分钟/6 小时接受，29 分钟/6 小时 1 分拒绝。</li>
 *   <li>{@link #rejectWithEmptyComment_isRejected}：REJECT 空理由 400，状态不推进（ApprovalCommand 拒绝必须填写意见）。</li>
 *   <li>{@link #approveWithEmptyComment_isLegal}：APPROVE 合法空意见成功并落审计（02 契约：意见允许空字符串）。
 *       已知 AN 缺陷（合法空意见被用作空事件摘要导致 400，见 integration-review-2026-11-02 §4）会使本例失败，
 *       保留契约预期作为缺陷证据，不改断言迁就实现。</li>
 *   <li>{@link #eligibility_teacherOwnerStudentOnlyVenueRejectedNoReservation}：教师所有者申请仅学生活动室被拒且无预约。</li>
 *   <li>{@link #changeOnlyOwnerStaff_goesStraightToApprover} 与 {@link #changeOwnerPlusOtherStaff_ownerHasNoSelfConfirmationRow}：
 *       所有者不向自己确认（BR-07/BR-23）。已知 CM 缺陷（ChangeService 将全部 staff 纳入 requiredStaffUserIds，
 *       同 integration-review-2026-11-02 §4）会使两例失败，保留契约预期，不修改生产。</li>
 * </ul>
 *
 * <p>执行命令（在仓库根目录，需 TEST_DATABASE_URL/TEST_DB_USERNAME/TEST_DB_PASSWORD/DEMO_PASSWORD 指向
 * campus_test 隔离库，03 契约 §3）：
 * {@code ./server/mvnw -f server/pom.xml "-Dit.test=ContractBoundaryIT" verify}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("契约边界回归：标题/时长/资格/审批意见/改期确认（ContractBoundaryIT）")
class ContractBoundaryIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final UUID USER_APPLICANT_A = UUID.fromString("00000000-0000-0000-0000-000000002001");
    private static final UUID USER_APPROVER_A = UUID.fromString("00000000-0000-0000-0000-000000002003");
    private static final UUID USER_STAFF_A = UUID.fromString("00000000-0000-0000-0000-000000002006");

    /** 演示数据未占用的空闲场地，避免与活动 A/B 的既有预约相撞。 */
    private static final UUID VENUE_3102 = UUID.fromString("00000000-0000-0000-0000-000000003102");
    /** 资格资源：仅学生活动室 E（allowTeacher=false，allowStudent=true，03 §1）。 */
    private static final UUID VENUE_STUDENT_ONLY = UUID.fromString("00000000-0000-0000-0000-000000003113");

    @RegisterExtension
    static final DbTestSupport db = DbTestSupport.create("demo");

    @Autowired
    private TestRestTemplate rest;

    // ---------------------------------------------------------------- 标题边界（02 §附加校验：title 1—100）

    @Test
    @DisplayName("标题边界：1/100 字接受（201），0/101 字拒绝（400 VALIDATION_ERROR）")
    void titleBoundary_1and100Accepted_0and101Rejected() {
        String owner = login("applicant_a");

        assertRejected(createActivity(owner, "", 17, 0), "空标题必须拒绝");
        assertRejected(createActivity(owner, "标".repeat(101), 17, 30), "101 字标题必须拒绝");

        HttpResult oneChar = createActivity(owner, "题", 18, 0);
        assertEquals(201, oneChar.status(), () -> "1 字标题应接受：" + oneChar.body());
        HttpResult hundredChar = createActivity(owner, "标".repeat(100), 18, 30);
        assertEquals(201, hundredChar.status(), () -> "100 字标题应接受：" + hundredChar.body());
    }

    // ---------------------------------------------------------------- 时长边界（01-基线 §3：30 分钟～6 小时）

    @Test
    @DisplayName("时长边界：30 分钟/6 小时接受（201），29 分钟/6 小时 1 分拒绝（400 VALIDATION_ERROR）")
    void durationBoundary_30minAnd6hAccepted_29minAnd6h1mRejected() {
        String owner = login("applicant_a");

        HttpResult tooShort = createActivity(owner, "边界-29分钟（测试数据）", "2030-10-21T10:00:00+08:00",
                "2030-10-21T10:29:00+08:00");
        assertRejected(tooShort, "29 分钟活动必须拒绝");

        HttpResult tooLong = createActivity(owner, "边界-6小时1分（测试数据）", "2030-10-21T12:00:00+08:00",
                "2030-10-21T18:01:00+08:00");
        assertRejected(tooLong, "6 小时 1 分活动必须拒绝");

        HttpResult halfHour = createActivity(owner, "边界-30分钟（测试数据）", "2030-10-21T10:00:00+08:00",
                "2030-10-21T10:30:00+08:00");
        assertEquals(201, halfHour.status(), () -> "30 分钟活动应接受：" + halfHour.body());

        HttpResult sixHours = createActivity(owner, "边界-6小时（测试数据）", "2030-10-21T12:00:00+08:00",
                "2030-10-21T18:00:00+08:00");
        assertEquals(201, sixHours.status(), () -> "6 小时活动应接受：" + sixHours.body());
    }

    // ---------------------------------------------------------------- 审批意见边界（02 契约：意见允许空字符串；拒绝理由至少 1 字）

    @Test
    @DisplayName("REJECT 空理由必须 400 且不推进状态（拒绝理由至少 1 字）")
    void rejectWithEmptyComment_isRejected() {
        String owner = login("applicant_a");
        String approver = login("approver_a");
        String activityId = createSubmittedActivity(owner, "边界-拒绝空理由（测试数据）",
                time(8, 0), time(9, 0), List.of());
        UUID activityUuid = UUID.fromString(activityId);
        int lockVersion = readLockVersion(owner, activityId);

        HttpResult rejected = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/approver-decision",
                approver, idem(), toJson(decisionBody("REJECT", "", lockVersion)));
        assertRejected(rejected, "REJECT 空理由必须拒绝");
        assertEquals("PENDING_APPROVER", db.activityStatus(activityUuid), "非法拒绝请求不得推进活动状态");
        assertEquals(0L, countWhere(
                "SELECT count(*) FROM activity_approval WHERE activity_id = ? AND decision IS NOT NULL", activityUuid),
                "非法拒绝请求不得写入审批决定");
    }

    @Test
    @DisplayName("APPROVE 合法空意见应成功并落审计（意见允许空字符串；已知 AN 缺陷会使本例失败）")
    void approveWithEmptyComment_isLegal() {
        String owner = login("applicant_a");
        String approver = login("approver_a");
        String activityId = createSubmittedActivity(owner, "边界-合法空审批意见（测试数据）",
                time(9, 0), time(10, 0), List.of());
        UUID activityUuid = UUID.fromString(activityId);
        int lockVersion = readLockVersion(owner, activityId);

        HttpResult approved = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/approver-decision",
                approver, idem(), toJson(decisionBody("APPROVE", "", lockVersion)));
        // 契约正确预期：02 §附加校验「意见允许空字符串」+ ApprovalCommand 仅拒绝要求非空。
        // 不得以非空意见替代本专项；若 400 则为 AN 合法空意见缺陷证据（EventPort 空事件摘要）。
        assertEquals(200, approved.status(), () -> "APPROVE 空意见是合法请求，应成功：" + approved.body());
        assertEquals("APPROVED_UNRESERVED", db.activityStatus(activityUuid), "同意后活动应进入 APPROVED_UNRESERVED");
        assertEquals(1L, countWhere(
                "SELECT count(*) FROM audit_log WHERE activity_id = ? AND event_type = 'ACTIVITY_APPROVED'", activityUuid),
                "合法空意见审批必须落审计事件");
    }

    // ---------------------------------------------------------------- 资格边界（N-AT-02：受限申请被拒且无预约）

    @Test
    @DisplayName("教师所有者申请仅学生活动室：提交必须 403 RESOURCE_INELIGIBLE 且无任何写入/预约")
    void eligibility_teacherOwnerStudentOnlyVenueRejectedNoReservation() {
        String owner = login("applicant_a"); // applicant_a 为 TEACHER 身份（03 §1）
        long activitiesBefore = countWhere("SELECT count(*) FROM activity");
        long reservationsBefore = countWhere("SELECT count(*) FROM reservation");
        ObjectNode input = activityInput("边界-资格受限（测试数据）", 10, 5, List.of(),
                planNode(time(16, 0), time(17, 0), 0, 0, VENUE_STUDENT_ONLY, null, 0));

        HttpResult created = call(HttpMethod.POST, "/api/v1/activities", owner, idem(), toJson(input));
        if (created.status() == 403) {
            // 资格校验前移到创建：必须 403 且完全无写入（快照计数核验）
            assertIneligible(created);
            assertEquals(activitiesBefore, countWhere("SELECT count(*) FROM activity"),
                    "资格拒绝不得写入活动行");
            assertEquals(reservationsBefore, countWhere("SELECT count(*) FROM reservation"),
                    "资格拒绝不得写入任何预约行");
            return;
        }
        assertEquals(201, created.status(), () -> "建草稿失败：" + created.body());
        String activityId = created.data().path("id").asText();
        UUID activityUuid = UUID.fromString(activityId);

        // 契约（01-基线 §2）：资格校验覆盖提交、候选、改期创建和最终确认——
        // create 成功时 submit 必须 403；submit 200 不得放行继续最终确认（否则掩盖提交校验缺口）
        HttpResult submitted = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/submit",
                owner, idem(), toJson(versionCommand(created.data().path("lockVersion").asInt())));
        assertIneligible(submitted);
        assertEquals("DRAFT", db.activityStatus(activityUuid), "提交被拒不得推进活动状态");
        assertEquals(0L, activeReservations(activityUuid), "资格受限不得产生 ACTIVE 预约");
        assertEquals(activitiesBefore + 1, countWhere("SELECT count(*) FROM activity"),
                "只允许草稿 1 行，不得有其他活动写入");
        assertEquals(reservationsBefore, countWhere("SELECT count(*) FROM reservation"),
                "资格受限不得写入任何预约行");
    }

    // ---------------------------------------------------------------- 改期确认边界（BR-07/BR-23：所有者不向自己确认）

    @Test
    @DisplayName("改期：staff 仅所有者时无内部确认，创建后直接待复审 PENDING_APPROVER")
    void changeOnlyOwnerStaff_goesStraightToApprover() {
        String owner = login("applicant_a");
        String activityId = scheduleActivityWithStaff(owner, "边界-仅所有者staff（测试数据）",
                time(10, 0), time(11, 0), List.of(USER_APPLICANT_A));
        UUID activityUuid = UUID.fromString(activityId);
        int lockVersion = readLockVersion(owner, activityId);

        HttpResult change = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/changes",
                owner, idem(), toJson(changeCreateBody(1, time(11, 0), time(12, 0), 0, 0,
                        VENUE_3102, null, 0, lockVersion)));
        assertEquals(201, change.status(), () -> "创建改期失败：" + change.body());
        // BR-07/BR-23：所有者不需要向自己确认；无其他工作人员时直接复审（01-基线 §3）
        assertEquals("PENDING_APPROVER", change.data().path("status").asText(),
                () -> "仅所有者 staff 的改期应直接待复审，实测：" + change.body());
        UUID changeUuid = UUID.fromString(change.data().path("id").asText());
        assertEquals(0L, countWhere("SELECT count(*) FROM change_confirmation WHERE change_id = ?", changeUuid),
                "所有者不向自己确认，不得预建任何工作人员待确认行");
        assertTrue(activityUuid != null, "活动已创建");
    }

    @Test
    @DisplayName("改期：staff 含所有者+他人时仅他人有待确认行，所有者无自我确认行")
    void changeOwnerPlusOtherStaff_ownerHasNoSelfConfirmationRow() {
        String owner = login("applicant_a");
        String activityId = scheduleActivityWithStaff(owner, "边界-所有者加他人staff（测试数据）",
                time(14, 0), time(15, 0), List.of(USER_APPLICANT_A, USER_STAFF_A));
        int lockVersion = readLockVersion(owner, activityId);

        HttpResult change = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/changes",
                owner, idem(), toJson(changeCreateBody(1, time(15, 0), time(16, 0), 0, 0,
                        VENUE_3102, null, 0, lockVersion)));
        assertEquals(201, change.status(), () -> "创建改期失败：" + change.body());
        assertEquals("AWAITING_CONFIRMATIONS", change.data().path("status").asText(),
                () -> "存在其他工作人员时应待其确认，实测：" + change.body());
        UUID changeUuid = UUID.fromString(change.data().path("id").asText());
        assertEquals(0L, countWhere(
                        "SELECT count(*) FROM change_confirmation WHERE change_id = ? AND user_id = ?", changeUuid,
                        USER_APPLICANT_A),
                "BR-07/BR-23：所有者不向自己确认，不得为所有者预建待确认行");
        assertEquals(1L, countWhere(
                        "SELECT count(*) FROM change_confirmation WHERE change_id = ? AND user_id = ?", changeUuid,
                        USER_STAFF_A),
                "工作人员 staff_a 应恰有 1 条待确认行");
    }

    // ---------------------------------------------------------------- 流程支撑

    /** 创建活动（API-12）并返回结果；不做成功断言，供边界用例断言。 */
    private HttpResult createActivity(String ownerToken, String title, int startHour, int startMinute) {
        return createActivity(ownerToken, title, time(startHour, startMinute), time(startHour + 1, startMinute));
    }

    private HttpResult createActivity(String ownerToken, String title, String startAt, String endAt) {
        ObjectNode input = activityInput(title, 10, 5, List.of(),
                planNode(startAt, endAt, 0, 0, VENUE_3102, null, 0));
        return call(HttpMethod.POST, "/api/v1/activities", ownerToken, idem(), toJson(input));
    }

    private void assertRejected(HttpResult result, String label) {
        assertEquals(400, result.status(), () -> label + "（应 400）：" + result.body());
        assertEquals("VALIDATION_ERROR", result.errorCode(), () -> label + " 错误码：" + result.body());
    }

    private void assertIneligible(HttpResult result) {
        assertEquals(403, result.status(), () -> "资格不足应返回 403：" + result.body());
        assertEquals("RESOURCE_INELIGIBLE", result.errorCode(), () -> "资格不足错误码：" + result.body());
        String details = result.error().path("details").toString();
        assertTrue(details.contains(VENUE_STUDENT_ONLY.toString()),
                () -> "details 应含受限资源 ID：" + details);
        assertTrue(details.contains("TEACHER"), () -> "details 应含所有者身份 TEACHER：" + details);
    }

    /** 创建→提交（API-12/16）的活动，返回活动 ID。 */
    private String createSubmittedActivity(String ownerToken, String title, String startAt, String endAt,
                                           List<UUID> staff) {
        ObjectNode input = activityInput(title, 10, 5, staff, planNode(startAt, endAt, 0, 0, VENUE_3102, null, 0));
        HttpResult created = call(HttpMethod.POST, "/api/v1/activities", ownerToken, idem(), toJson(input));
        assertEquals(201, created.status(), () -> "创建活动失败：" + created.body());
        String activityId = created.data().path("id").asText();
        HttpResult submitted = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/submit",
                ownerToken, idem(), toJson(versionCommand(created.data().path("lockVersion").asInt())));
        assertEquals(200, submitted.status(), () -> "提交活动失败：" + submitted.body());
        return activityId;
    }

    /** 创建→提交→审批→资源确认→发布（API-12/16/18/19/20），返回 OPEN 活动 ID。 */
    private String scheduleActivityWithStaff(String ownerToken, String title, String startAt, String endAt,
                                             List<UUID> staff) {
        String activityId = createSubmittedActivity(ownerToken, title, startAt, endAt, staff);
        String approver = login("approver_a");
        String resourceAdmin = login("resource_admin");

        HttpResult approved = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/approver-decision",
                approver, idem(), toJson(decisionBody("APPROVE", "边界回归同意（测试数据）",
                        readLockVersion(ownerToken, activityId))));
        assertEquals(200, approved.status(), () -> "审批失败：" + approved.body());

        HttpResult confirmed = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/resource-confirm",
                resourceAdmin, idem(), toJson(versionCommand(approved.data().path("lockVersion").asInt())));
        assertEquals(200, confirmed.status(), () -> "资源确认失败：" + confirmed.body());

        HttpResult published = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/publish",
                ownerToken, idem(), toJson(versionCommand(confirmed.data().path("lockVersion").asInt())));
        assertEquals(200, published.status(), () -> "发布失败：" + published.body());
        return activityId;
    }

    // ---------------------------------------------------------------- HTTP 基础

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
        String value = System.getenv("DEMO_PASSWORD");
        assertNotNull(value, "缺少 DEMO_PASSWORD 环境变量（03 §1/§3）");
        assertFalse(value.isBlank(), "DEMO_PASSWORD 不能为空");
        HttpResult result = call(HttpMethod.POST, "/api/v1/auth/login", null, null,
                toJson(loginBody(username, value)));
        assertEquals(200, result.status(), () -> username + " 登录失败：" + result.body());
        return result.data().path("token").asText();
    }

    private int readLockVersion(String token, String activityId) {
        HttpResult result = call(HttpMethod.GET, "/api/v1/activities/" + activityId, token, null, null);
        assertEquals(200, result.status(), () -> "读取活动失败：" + result.body());
        return result.data().path("lockVersion").asInt();
    }

    // ---------------------------------------------------------------- 落库辅助

    private long countWhere(String sql, Object... args) {
        Long value = db.jdbc().queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private long activeReservations(UUID activityId) {
        return countWhere("SELECT count(*) FROM reservation WHERE activity_id = ? AND status = 'ACTIVE'", activityId);
    }

    // ---------------------------------------------------------------- JSON 构造

    private static String time(int hour, int minute) {
        return String.format("2030-10-20T%02d:%02d:00+08:00", hour, minute);
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

    private static ObjectNode changeCreateBody(int baseVersionNo, String startAt, String endAt,
                                               int setupMinutes, int teardownMinutes, UUID venueId,
                                               UUID equipmentId, int equipmentQuantity, int expectedLockVersion) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("baseVersionNo", baseVersionNo);
        node.set("targetPlan", planNode(startAt, endAt, setupMinutes, teardownMinutes,
                venueId, equipmentId, equipmentQuantity));
        node.put("expectedLockVersion", expectedLockVersion);
        node.put("reason", "边界回归改期到空闲时段（测试数据）");
        return node;
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

    private static String idem() {
        return UUID.randomUUID().toString();
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
