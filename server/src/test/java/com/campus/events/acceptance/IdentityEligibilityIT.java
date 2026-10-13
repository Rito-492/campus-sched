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
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * N-AT-01～N-AT-06 身份、资格与授权范围专项验收测试（IdentityEligibility）。
 *
 * <p>承担新版任务包（{@code agents/common/03-验收与演示数据.md} §6）新增的六项身份/资格/授权范围场景：
 * <ul>
 *   <li>N-AT-01 {@link #nat01_identitySeparateFromRole}：身份与角色分离，教师身份无审批权、账号管理员无业务权限。</li>
 *   <li>N-AT-02 {@link #nat02_eligibilityMatrixEightCombos}：两类身份 × 四组资源资格共 8 组组合，允许建约、拒绝 403 RESOURCE_INELIGIBLE。</li>
 *   <li>N-AT-03 {@link #nat03_candidateFiltersIneligibleAndDirectSubmitRejected}：候选过滤不合资格资源，强制直提仍被拒，资格按活动所有者身份。</li>
 *   <li>N-AT-04 {@link #nat04_identityChangeUsesLatestForPending}：未确认申请按最新身份重新校验，已生效预约不被删除，改期新目标不合资格被拒且原安排不变。</li>
 *   <li>N-AT-05 {@link #nat05_ownerCannotSelfApproveOrConfirm}：所有者兼审批/复核人对自己 4 种审核全部 403，独立授权人员正常成功。</li>
 *   <li>N-AT-06 {@link #nat06_scopePartialAndDisabledStaff}：审批组织/类别范围不全、资源范围不全、工作人员停用后拒绝且不代签、可撤回重建。</li>
 * </ul>
 *
 * <p>装载数据：{@code test-data/demo.json}（固定 Clock=2030-10-19T00:00:00Z、DEMO_DATE=2030-10-20）。
 * 账号补充配置（03 §1）：applicant_a 为 TEACHER 身份 APPLICANT、applicant_b 为 STUDENT 身份 APPLICANT；
 * approver_a/approver_other 为 TEACHER 身份 APPROVER；resource_admin 为 TEACHER 身份 ADMIN；
 * staff_a/student_a/student_b 为 STUDENT 身份 APPLICANT。
 * 附加独立资格资源四组：场地 3111（双允许）/3112（仅教师）/3113（仅学生）/3114（双禁止），
 * 设备 3211/3212/3213/3214 依序对应。并发使用 REST 直接 HTTP，断言全部落库（jdbc）。
 *
 * <p>执行命令（在仓库根目录）：
 * {@code ./server/mvnw.cmd -f server/pom.xml "-Dit.test=IdentityEligibilityIT" verify}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("N-AT-01～06 身份、资格与授权范围（IdentityEligibilityIT）")
class IdentityEligibilityIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final UUID ORG_1001 = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID ORG_1002 = UUID.fromString("00000000-0000-0000-0000-000000001002");

    private static final UUID USER_APPLICANT_A = UUID.fromString("00000000-0000-0000-0000-000000002001");
    private static final UUID USER_APPLICANT_B = UUID.fromString("00000000-0000-0000-0000-000000002002");
    private static final UUID USER_APPROVER_A = UUID.fromString("00000000-0000-0000-0000-000000002003");
    private static final UUID USER_RESOURCE_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000002004");
    private static final UUID USER_STUDENT_A = UUID.fromString("00000000-0000-0000-0000-000000002005");
    private static final UUID USER_STAFF_A = UUID.fromString("00000000-0000-0000-0000-000000002006");
    private static final UUID USER_ACCOUNT_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000002007");
    private static final UUID USER_APPROVER_OTHER = UUID.fromString("00000000-0000-0000-0000-000000002008");
    private static final UUID USER_STUDENT_B = UUID.fromString("00000000-0000-0000-0000-000000002009");

    private static final UUID ACTIVITY_A = UUID.fromString("00000000-0000-0000-0000-000000004001");
    private static final UUID ACTIVITY_B = UUID.fromString("00000000-0000-0000-0000-000000004002");

    private static final UUID VENUE_3101 = UUID.fromString("00000000-0000-0000-0000-000000003101");
    private static final UUID VENUE_3102 = UUID.fromString("00000000-0000-0000-0000-000000003102");
    private static final UUID EQUIP_3201 = UUID.fromString("00000000-0000-0000-0000-000000003201");
    private static final UUID EQUIP_3202 = UUID.fromString("00000000-0000-0000-0000-000000003202");

    private static final UUID R3111 = UUID.fromString("00000000-0000-0000-0000-000000003111");
    private static final UUID R3112 = UUID.fromString("00000000-0000-0000-0000-000000003112");
    private static final UUID R3113 = UUID.fromString("00000000-0000-0000-0000-000000003113");
    private static final UUID R3114 = UUID.fromString("00000000-0000-0000-0000-000000003114");
    private static final UUID R3211 = UUID.fromString("00000000-0000-0000-0000-000000003211");
    private static final UUID R3212 = UUID.fromString("00000000-0000-0000-0000-000000003212");
    private static final UUID R3213 = UUID.fromString("00000000-0000-0000-0000-000000003213");
    private static final UUID R3214 = UUID.fromString("00000000-0000-0000-0000-000000003214");

    private static final String NEW_USER_PASSWORD = "Passw0rd!2030";

    @RegisterExtension
    static final DbTestSupport db = DbTestSupport.create("demo");

    @Autowired
    private TestRestTemplate rest;

    // ---------------------------------------------------------------- N-AT-01

    @Test
    @DisplayName("N-AT-01 教师申请人/学生申请人/账号管理员均无审批或资源复核权限")
    void nat01_identitySeparateFromRole() {
        String ownerB = login("applicant_b");
        String applicantA = login("applicant_a");
        String studentA = login("student_a");
        String accountAdmin = login("account_admin");

        String activityId = createSubmittedActivity(ownerB, "N-AT-01 身份与角色分离活动（测试数据）",
                "2030-10-20T18:00:00+08:00", "2030-10-20T19:00:00+08:00", 0, 0,
                VENUE_3101, EQUIP_3201, 1, 10, 5);
        UUID activityUuid = UUID.fromString(activityId);

        assertEquals("PENDING_APPROVER", db.activityStatus(activityUuid));
        long approvalsBefore = countWhere("SELECT count(*) FROM activity_approval WHERE activity_id = ?", activityUuid);

        int lock = readActivityLockVersion(ownerB, activityId);

        HttpResult approverDecision = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/approver-decision",
                applicantA, idem(), toJson(decisionBody("APPROVE", "", lock)));
        assertEquals(403, approverDecision.status(), () -> "教师身份不应自动获得审批权：" + approverDecision.body());
        assertEquals("FORBIDDEN", approverDecision.errorCode());

        HttpResult studentDecision = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/approver-decision",
                studentA, idem(), toJson(decisionBody("APPROVE", "", lock)));
        assertEquals(403, studentDecision.status(), () -> "学生身份不应获得审批权：" + studentDecision.body());
        assertEquals("FORBIDDEN", studentDecision.errorCode());

        HttpResult accountDecision = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/approver-decision",
                accountAdmin, idem(), toJson(decisionBody("APPROVE", "", lock)));
        assertEquals(403, accountDecision.status(), () -> "账号管理员不应自动获得审批权：" + accountDecision.body());
        assertEquals("FORBIDDEN", accountDecision.errorCode());

        HttpResult accountConfirm = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/resource-confirm",
                accountAdmin, idem(), toJson(versionCommand(lock)));
        assertEquals(403, accountConfirm.status(), () -> "账号管理员不应自动获得资源复核权限：" + accountConfirm.body());
        assertEquals("FORBIDDEN", accountConfirm.errorCode());

        assertEquals("PENDING_APPROVER", db.activityStatus(activityUuid), "活动状态不得变化");
        assertEquals(approvalsBefore, countWhere(
                "SELECT count(*) FROM activity_approval WHERE activity_id = ?", activityUuid), "审批记录不得新增");
        assertEquals(0L, countWhere(
                "SELECT count(*) FROM reservation WHERE activity_id = ? AND status = 'ACTIVE'", activityUuid),
                "未确认活动不得产生预约");
        assertEquals(0L, countWhere("SELECT count(*) FROM user_approval_scope WHERE user_id = ?", USER_ACCOUNT_ADMIN),
                "账号管理员不应有审批范围");
        assertEquals(0L, countWhere("SELECT count(*) FROM user_permission WHERE user_id = ? AND permission = 'RESOURCE_CONFIRM'",
                USER_ACCOUNT_ADMIN), "账号管理员不应有 RESOURCE_CONFIRM 权限");
    }

    // ---------------------------------------------------------------- N-AT-02

    @Test
    @DisplayName("N-AT-02 两类身份 × 四组资源资格 8 组组合：允许建约、受限 403 RESOURCE_INELIGIBLE")
    void nat02_eligibilityMatrixEightCombos() {
        String teacherToken = login("applicant_a");
        String studentToken = login("applicant_b");
        String approverToken = login("approver_a");
        String resourceToken = login("resource_admin");

        List<Combo> combos = List.of(
                new Combo("教师×双允许(3111/3211)", "TEACHER", R3111, R3211, true, 8, 0),
                new Combo("教师×仅教师(3112/3212)", "TEACHER", R3112, R3212, true, 9, 0),
                new Combo("教师×仅学生(3113/3213)", "TEACHER", R3113, R3213, false, 10, 0),
                new Combo("教师×双禁止(3114/3214)", "TEACHER", R3114, R3214, false, 11, 0),
                new Combo("学生×双允许(3111/3211)", "STUDENT", R3111, R3211, true, 12, 0),
                new Combo("学生×仅教师(3112/3212)", "STUDENT", R3112, R3212, false, 13, 0),
                new Combo("学生×仅学生(3113/3213)", "STUDENT", R3113, R3213, true, 14, 0),
                new Combo("学生×双禁止(3114/3214)", "STUDENT", R3114, R3214, false, 15, 0));

        for (Combo combo : combos) {
            String ownerToken = "TEACHER".equals(combo.identity()) ? teacherToken : studentToken;
            verifyCombo(combo, ownerToken, approverToken, resourceToken);
        }
    }

    // ---------------------------------------------------------------- N-AT-03

    @Test
    @DisplayName("N-AT-03 候选过滤受限资源、强制直提被拒，资格按活动所有者身份")
    void nat03_candidateFiltersIneligibleAndDirectSubmitRejected() {
        String ownerB = login("applicant_b");

        int baseVersion = readActivityVersion(ownerB, ACTIVITY_B.toString());
        ObjectNode proposal = MAPPER.createObjectNode();
        proposal.put("baseVersionNo", baseVersion);
        proposal.set("targetPlan", planNode("2030-10-20T18:00:00+08:00", "2030-10-20T19:00:00+08:00",
                0, 0, R3112, R3212, 1));

        HttpResult alternatives = call(HttpMethod.POST, "/api/v1/activities/" + ACTIVITY_B + "/alternatives",
                ownerB, null, toJson(proposal));
        assertEquals(200, alternatives.status(), () -> "候选生成失败：" + alternatives.body());
        JsonNode items = alternatives.data().path("items");
        assertTrue(items.isArray(), "AlternativesView.items 应为数组");
        for (JsonNode item : items) {
            JsonNode plan = item.path("plan");
            assertNotEquals(R3112.toString(), plan.path("venueId").asText(),
                    "候选不得含仅教师场地 3112（学生所有者）：" + item);
            for (JsonNode equipment : plan.path("equipment")) {
                assertNotEquals(R3212.toString(), equipment.path("resourceId").asText(),
                        "候选不得含仅教师设备 3212（学生所有者）：" + item);
            }
        }

        long changesBefore = countWhere("SELECT count(*) FROM change_request WHERE activity_id = ?", ACTIVITY_B);
        int lock = readActivityLockVersion(ownerB, ACTIVITY_B.toString());
        ObjectNode forced = changeCreateBody(baseVersion, "2030-10-20T18:00:00+08:00", "2030-10-20T19:00:00+08:00",
                0, 0, R3112, R3212, 1, lock);
        HttpResult rejected = call(HttpMethod.POST, "/api/v1/activities/" + ACTIVITY_B + "/changes",
                ownerB, idem(), toJson(forced));
        assertIneligible(rejected, "STUDENT", R3112, R3212);
        assertEquals(changesBefore, countWhere("SELECT count(*) FROM change_request WHERE activity_id = ?", ACTIVITY_B),
                "资格不足的直提不得创建改期记录");
    }

    // ---------------------------------------------------------------- N-AT-04

    @Test
    @DisplayName("N-AT-04 未确认申请用最新身份校验、生效预约保留、改期新目标不合资格被拒且原安排不变")
    void nat04_identityChangeUsesLatestForPending() {
        String admin = login("account_admin");
        String approver = login("approver_a");
        String resource = login("resource_admin");

        String ownerId = createUser(admin, "nat04_owner", "N-AT-04 所有者（教师）", ORG_1001, "TEACHER",
                List.of("APPLICANT"), List.of(), List.of(), List.of());
        String owner = loginWith("nat04_owner", NEW_USER_PASSWORD);
        UUID ownerUuid = UUID.fromString(ownerId);
        assertEquals("TEACHER", queryString("SELECT identity FROM app_user WHERE id = ?", ownerUuid));

        try {
            String t1 = createSubmittedActivity(owner, "N-AT-04 未确认申请活动（测试数据）",
                    "2030-10-20T16:00:00+08:00", "2030-10-20T17:00:00+08:00", 0, 0,
                    R3112, R3212, 1, 10, 5);
            UUID t1Uuid = UUID.fromString(t1);
            assertEquals("PENDING_APPROVER", db.activityStatus(t1Uuid));

            updateIdentity(admin, ownerUuid, "STUDENT");
            assertEquals("STUDENT", queryString("SELECT identity FROM app_user WHERE id = ?", ownerUuid),
                    "API-39 身份变更应落库");

            HttpResult approved = call(HttpMethod.POST, "/api/v1/activities/" + t1 + "/approver-decision",
                    approver, idem(), toJson(decisionBody("APPROVE", "",
                            readActivityLockVersion(owner, t1))));
            assertEquals(200, approved.status(), () -> "审批失败：" + approved.body());

            HttpResult confirm = call(HttpMethod.POST, "/api/v1/activities/" + t1 + "/resource-confirm",
                    resource, idem(), toJson(versionCommand(readActivityLockVersion(owner, t1))));
            assertIneligible(confirm, "STUDENT", R3112, R3212);
            assertEquals(0L, countWhere(
                    "SELECT count(*) FROM reservation WHERE activity_id = ? AND status = 'ACTIVE'", t1Uuid),
                    "未确认申请按最新身份重新校验后不得写入预约");

            updateIdentity(admin, ownerUuid, "TEACHER");

            String t2 = createSubmittedActivity(owner, "N-AT-04 生效预约活动（测试数据）",
                    "2030-10-20T17:00:00+08:00", "2030-10-20T18:00:00+08:00", 0, 0,
                    R3112, R3212, 1, 10, 5);
            UUID t2Uuid = UUID.fromString(t2);
            int lock = readActivityLockVersion(owner, t2);
            HttpResult approved2 = call(HttpMethod.POST, "/api/v1/activities/" + t2 + "/approver-decision",
                    approver, idem(), toJson(decisionBody("APPROVE", "", lock)));
            assertEquals(200, approved2.status(), () -> "审批失败：" + approved2.body());
            HttpResult confirmed2 = call(HttpMethod.POST, "/api/v1/activities/" + t2 + "/resource-confirm",
                    resource, idem(), toJson(versionCommand(readActivityLockVersion(owner, t2))));
            assertEquals(200, confirmed2.status(), () -> "资源确认失败：" + confirmed2.body());
            assertEquals("SCHEDULED", confirmed2.data().path("status").asText());

            List<String> activeBefore = activeResourceIds(t2Uuid);
            assertTrue(activeBefore.contains(R3112.toString()) && activeBefore.contains(R3212.toString()),
                    "生效预约应含 3112 与 3212：" + activeBefore);
            Integer versionBefore = db.effectiveVersionNo(t2Uuid);

            updateIdentity(admin, ownerUuid, "STUDENT");

            assertEquals(activeBefore, activeResourceIds(t2Uuid), "已生效预约不因资格变更被删除");
            assertEquals(versionBefore, db.effectiveVersionNo(t2Uuid), "资格变更不得改动活动有效版本");

            int baseVersion = readActivityVersion(owner, t2);
            int lock2 = readActivityLockVersion(owner, t2);
            ObjectNode change = changeCreateBody(baseVersion, "2030-10-20T19:00:00+08:00", "2030-10-20T20:00:00+08:00",
                    0, 0, R3112, R3212, 1, lock2);
            HttpResult rejected = call(HttpMethod.POST, "/api/v1/activities/" + t2 + "/changes",
                    owner, idem(), toJson(change));
            assertIneligible(rejected, "STUDENT", R3112, R3212);

            assertEquals(activeBefore, activeResourceIds(t2Uuid), "改期被拒后原 ACTIVE 预约内容不变");
            assertEquals(versionBefore, db.effectiveVersionNo(t2Uuid), "改期被拒后活动有效版本不变");
        } finally {
            updateIdentity(admin, ownerUuid, "TEACHER");
        }
    }

    // ---------------------------------------------------------------- N-AT-05

    @Test
    @DisplayName("N-AT-05 所有者兼任审批/复核人对自己 4 种审核全部 403，独立授权人员正常成功")
    void nat05_ownerCannotSelfApproveOrConfirm() {
        String admin = login("account_admin");
        String approver = login("approver_a");
        String resource = login("resource_admin");

        String combinedId = createUser(admin, "nat05_combined", "N-AT-05 兼任所有角色（测试数据）", ORG_1001,
                "TEACHER", List.of("APPLICANT", "APPROVER", "ADMIN"), List.of("RESOURCE_CONFIRM"),
                List.of(scope(ORG_1001, List.of("VENUE", "PROJECTOR"))),
                List.of(VENUE_3101, EQUIP_3201));
        UUID combinedUuid = UUID.fromString(combinedId);
        String combined = loginWith("nat05_combined", NEW_USER_PASSWORD);

        assertEquals(3L, countWhere("SELECT count(*) FROM user_role WHERE user_id = ?", combinedUuid),
                "兼任账号应有 APPLICANT/APPROVER/ADMIN 三个角色");
        assertEquals(1L, countWhere(
                "SELECT count(*) FROM user_permission WHERE user_id = ? AND permission = 'RESOURCE_CONFIRM'",
                combinedUuid), "兼任账号应有 RESOURCE_CONFIRM 权限");

        String activityId = createSubmittedActivity(combined, "N-AT-05 兼任回避活动（测试数据）",
                "2030-10-20T17:00:00+08:00", "2030-10-20T18:00:00+08:00", 0, 0,
                VENUE_3101, EQUIP_3201, 1, 10, 5);
        UUID activityUuid = UUID.fromString(activityId);

        long approvalsBefore = countWhere(
                "SELECT count(*) FROM activity_approval WHERE activity_id = ? AND actor_id = ?", activityUuid, combinedUuid);
        long reservationsBefore = countWhere("SELECT count(*) FROM reservation WHERE activity_id = ?", activityUuid);

        HttpResult selfApproval = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/approver-decision",
                combined, idem(), toJson(decisionBody("APPROVE", "", readActivityLockVersion(combined, activityId))));
        assertEquals(403, selfApproval.status(), () -> "所有者不得自我审批：" + selfApproval.body());
        assertEquals("FORBIDDEN", selfApproval.errorCode());
        assertEquals(approvalsBefore, countWhere(
                "SELECT count(*) FROM activity_approval WHERE activity_id = ? AND actor_id = ?", activityUuid, combinedUuid),
                "自我审批不得新增审批记录");

        HttpResult independentApproval = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/approver-decision",
                approver, idem(), toJson(decisionBody("APPROVE", "", readActivityLockVersion(combined, activityId))));
        assertEquals(200, independentApproval.status(), () -> "独立批准人应成功：" + independentApproval.body());

        HttpResult selfConfirm = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/resource-confirm",
                combined, idem(), toJson(versionCommand(readActivityLockVersion(combined, activityId))));
        assertEquals(403, selfConfirm.status(), () -> "所有者不得自我资源复核：" + selfConfirm.body());
        assertEquals("FORBIDDEN", selfConfirm.errorCode());
        assertEquals(reservationsBefore, countWhere("SELECT count(*) FROM reservation WHERE activity_id = ?", activityUuid),
                "自我资源复核不得写预约");

        HttpResult independentConfirm = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/resource-confirm",
                resource, idem(), toJson(versionCommand(readActivityLockVersion(combined, activityId))));
        assertEquals(200, independentConfirm.status(), () -> "独立复核人应成功：" + independentConfirm.body());
        assertEquals("SCHEDULED", independentConfirm.data().path("status").asText());
        assertTrue(countWhere("SELECT count(*) FROM reservation WHERE activity_id = ? AND status = 'ACTIVE'", activityUuid) >= 2,
                "独立复核后应建立 ACTIVE 预约");

        int baseVersion = readActivityVersion(combined, activityId);
        int lock = readActivityLockVersion(combined, activityId);
        ObjectNode change = changeCreateBody(baseVersion, "2030-10-20T19:00:00+08:00", "2030-10-20T20:00:00+08:00",
                0, 0, VENUE_3101, EQUIP_3201, 1, lock);
        HttpResult changeCreated = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/changes",
                combined, idem(), toJson(change));
        assertEquals(201, changeCreated.status(), () -> "创建改期失败：" + changeCreated.body());
        String changeId = changeCreated.data().path("id").asText();
        String changeStatus = changeCreated.data().path("status").asText();
        assertTrue("AWAITING_CONFIRMATIONS".equals(changeStatus) || "PENDING_APPROVER".equals(changeStatus),
                "无工作人员时应直接进入待审批：" + changeStatus);

        long activeBefore = countWhere(
                "SELECT count(*) FROM reservation WHERE activity_id = ? AND status = 'ACTIVE'", activityUuid);
        long selfApprovalsBefore = countWhere(
                "SELECT count(*) FROM change_request WHERE id = ? AND approver_decision_json IS NOT NULL",
                UUID.fromString(changeId));

        HttpResult selfChangeApproval = call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/approver-decision",
                combined, idem(), toJson(decisionBody("APPROVE", "")));
        assertEquals(403, selfChangeApproval.status(), () -> "所有者不得自我审批改期：" + selfChangeApproval.body());
        assertEquals("FORBIDDEN", selfChangeApproval.errorCode());
        assertEquals(selfApprovalsBefore, countWhere(
                "SELECT count(*) FROM change_request WHERE id = ? AND approver_decision_json IS NOT NULL",
                UUID.fromString(changeId)), "自我审批改期不得写入决定");

        HttpResult independentChangeApproval = call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/approver-decision",
                approver, idem(), toJson(decisionBody("APPROVE", "")));
        assertEquals(200, independentChangeApproval.status(), () -> "独立批准人改期审批应成功：" + independentChangeApproval.body());
        assertEquals("PENDING_RESOURCE", independentChangeApproval.data().path("status").asText());

        long resourceDecisionsBefore = countWhere(
                "SELECT count(*) FROM change_request WHERE id = ? AND resource_decision_json IS NOT NULL",
                UUID.fromString(changeId));
        HttpResult selfChangeDecision = call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/resource-decision",
                combined, idem(), toJson(decisionBody("APPROVE", "")));
        assertEquals(403, selfChangeDecision.status(), () -> "所有者不得自我资源复核改期：" + selfChangeDecision.body());
        assertEquals("FORBIDDEN", selfChangeDecision.errorCode());
        assertEquals(resourceDecisionsBefore, countWhere(
                "SELECT count(*) FROM change_request WHERE id = ? AND resource_decision_json IS NOT NULL",
                UUID.fromString(changeId)), "自我复核改期不得写入决定");
        assertEquals(activeBefore, countWhere(
                "SELECT count(*) FROM reservation WHERE activity_id = ? AND status = 'ACTIVE'", activityUuid),
                "自我复核被拒后原 ACTIVE 预约计数不变");

        HttpResult independentChangeDecision = call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/resource-decision",
                resource, idem(), toJson(decisionBody("APPROVE", "")));
        assertEquals(200, independentChangeDecision.status(), () -> "独立复核人改期复核应成功：" + independentChangeDecision.body());
        assertEquals("APPLIED", independentChangeDecision.data().path("status").asText());

        assertEquals(0L, countWhere(
                "SELECT count(*) FROM activity_approval WHERE activity_id = ? AND actor_id = ?", activityUuid, combinedUuid),
                "自我审核不得产生 activity_approval 记录");
    }

    // ---------------------------------------------------------------- N-AT-06

    @Test
    @DisplayName("N-AT-06 审批组织/类别范围不全、资源范围不全、工作人员停用后拒绝且不代签")
    void nat06_scopePartialAndDisabledStaff() {
        String admin = login("account_admin");
        String ownerA = login("applicant_a");
        String approverA = login("approver_a");
        String resourceAdmin = login("resource_admin");
        String approverOther = login("approver_other");

        // ① 组织范围越权：approver_other 仅覆盖组织 1002，审批组织 1001 活动
        String orgActivity = createSubmittedActivity(ownerA, "N-AT-06 组织越权活动（测试数据）",
                "2030-10-20T20:00:00+08:00", "2030-10-20T21:00:00+08:00", 0, 0,
                VENUE_3101, EQUIP_3201, 1, 10, 5);
        UUID orgActivityUuid = UUID.fromString(orgActivity);
        long orgApprovalsBefore = countWhere(
                "SELECT count(*) FROM activity_approval WHERE activity_id = ?", orgActivityUuid);
        HttpResult otherOrg = call(HttpMethod.POST, "/api/v1/activities/" + orgActivity + "/approver-decision",
                approverOther, idem(), toJson(decisionBody("APPROVE", "", readActivityLockVersion(ownerA, orgActivity))));
        assertEquals(403, otherOrg.status(), () -> "范围外组织审批应被拒：" + otherOrg.body());
        assertEquals("FORBIDDEN", otherOrg.errorCode());
        assertEquals("PENDING_APPROVER", db.activityStatus(orgActivityUuid), "越权审批不得改变活动状态");
        assertEquals(orgApprovalsBefore, countWhere(
                "SELECT count(*) FROM activity_approval WHERE activity_id = ?", orgActivityUuid), "越权审批不得新增审批记录");

        // ① 类别范围不全：仅 VENUE、缺 PROJECTOR 的批准人审批含 PROJECTOR 的活动
        String partialApproverId = createUser(admin, "nat06_partial_approver", "N-AT-06 类别不全批准人（测试数据）",
                ORG_1001, "TEACHER", List.of("APPROVER"), List.of(),
                List.of(scope(ORG_1001, List.of("VENUE"))), List.of());
        String partialApprover = loginWith("nat06_partial_approver", NEW_USER_PASSWORD);
        assertEquals(1L, countWhere("SELECT count(*) FROM user_approval_scope WHERE user_id = ?",
                UUID.fromString(partialApproverId)), "范围不全批准人应仅 1 条审批范围");

        String projectorActivity = createSubmittedActivity(ownerA, "N-AT-06 类别不全活动（测试数据）",
                "2030-10-20T20:00:00+08:00", "2030-10-20T21:00:00+08:00", 0, 0,
                VENUE_3102, EQUIP_3202, 1, 10, 5);
        UUID projectorActivityUuid = UUID.fromString(projectorActivity);
        HttpResult incomplete = call(HttpMethod.POST, "/api/v1/activities/" + projectorActivity + "/approver-decision",
                partialApprover, idem(),
                toJson(decisionBody("APPROVE", "", readActivityLockVersion(ownerA, projectorActivity))));
        assertEquals(403, incomplete.status(), () -> "复合资源须完整覆盖，缺 PROJECTOR 应被拒：" + incomplete.body());
        assertEquals("FORBIDDEN", incomplete.errorCode());
        assertEquals("PENDING_APPROVER", db.activityStatus(projectorActivityUuid), "范围不全审批不得改变活动状态");
        assertEquals(0L, countWhere("SELECT count(*) FROM activity_approval WHERE activity_id = ?", projectorActivityUuid),
                "范围不全审批不得新增审批记录");

        // ② 资源范围不全：仅管理 3101/3201，复核使用 3102/3202 的活动与登记该资源故障
        String partialAdminId = createUser(admin, "nat06_partial_admin", "N-AT-06 部分资源管理员（测试数据）",
                ORG_1001, "TEACHER", List.of("ADMIN"), List.of("RESOURCE_MANAGE", "RESOURCE_CONFIRM"),
                List.of(), List.of(VENUE_3101, EQUIP_3201));
        String partialAdmin = loginWith("nat06_partial_admin", NEW_USER_PASSWORD);
        UUID partialAdminUuid = UUID.fromString(partialAdminId);
        assertEquals(2L, countWhere("SELECT count(*) FROM user_resource_scope WHERE user_id = ?", partialAdminUuid),
                "部分资源管理员应仅 2 条资源范围");

        String outOfScopeActivity = createSubmittedActivity(ownerA, "N-AT-06 范围外复核活动（测试数据）",
                "2030-10-20T21:00:00+08:00", "2030-10-20T22:00:00+08:00", 0, 0,
                VENUE_3102, EQUIP_3202, 1, 10, 5);
        UUID outOfScopeUuid = UUID.fromString(outOfScopeActivity);
        HttpResult approved = call(HttpMethod.POST, "/api/v1/activities/" + outOfScopeActivity + "/approver-decision",
                approverA, idem(),
                toJson(decisionBody("APPROVE", "", readActivityLockVersion(ownerA, outOfScopeActivity))));
        assertEquals(200, approved.status(), () -> "审批失败：" + approved.body());

        HttpResult outOfScopeConfirm = call(HttpMethod.POST, "/api/v1/activities/" + outOfScopeActivity + "/resource-confirm",
                partialAdmin, idem(), toJson(versionCommand(readActivityLockVersion(ownerA, outOfScopeActivity))));
        assertEquals(403, outOfScopeConfirm.status(), () -> "管理范围外资源复核应被拒：" + outOfScopeConfirm.body());
        assertEquals("FORBIDDEN", outOfScopeConfirm.errorCode());
        assertEquals(0L, countWhere(
                "SELECT count(*) FROM reservation WHERE activity_id = ? AND status = 'ACTIVE'", outOfScopeUuid),
                "范围外复核不得写预约");

        ObjectNode faultBody = MAPPER.createObjectNode();
        faultBody.put("startAt", "2030-10-20T21:00:00+08:00");
        faultBody.put("endAt", "2030-10-20T22:00:00+08:00");
        faultBody.put("unavailableQuantity", 1);
        faultBody.put("reason", "N-AT-06 范围外故障登记（测试数据）");
        HttpResult fault = call(HttpMethod.POST, "/api/v1/resources/" + VENUE_3102 + "/faults",
                partialAdmin, idem(), toJson(faultBody));
        assertEquals(403, fault.status(), () -> "管理范围外资源故障登记应被拒：" + fault.body());
        assertEquals("FORBIDDEN", fault.errorCode());
        assertEquals(0L, countWhere("SELECT count(*) FROM resource_fault WHERE resource_id = ?", VENUE_3102),
                "范围外故障登记不得落库");

        // ③ 工作人员停用：确认请求被拒且不代签，改期可撤回重建
        String staffToken = login("staff_a");
        int baseVersion = readActivityVersion(ownerA, ACTIVITY_A.toString());
        int lock = readActivityLockVersion(ownerA, ACTIVITY_A.toString());
        ObjectNode change = changeCreateBody(baseVersion, "2030-10-20T12:30:00+08:00", "2030-10-20T13:30:00+08:00",
                30, 30, VENUE_3102, EQUIP_3202, 1, lock);
        HttpResult created = call(HttpMethod.POST, "/api/v1/activities/" + ACTIVITY_A + "/changes",
                ownerA, idem(), toJson(change));
        assertEquals(201, created.status(), () -> "创建改期失败：" + created.body());
        String changeId = created.data().path("id").asText();
        assertEquals("AWAITING_CONFIRMATIONS", created.data().path("status").asText());

        HttpResult disabled = call(HttpMethod.PATCH, "/api/v1/users/" + USER_STAFF_A + "/enabled",
                admin, idem(), toJson(boolBody("enabled", false)));
        assertEquals(200, disabled.status(), () -> "停用工作人员失败：" + disabled.body());
        assertFalse(queryBool("SELECT enabled FROM app_user WHERE id = ?", USER_STAFF_A), "staff_a 应已停用");

        try {
            HttpResult confirm = call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/confirmation",
                    staffToken, idem(), toJson(decisionBody("APPROVE", "")));
            assertTrue(confirm.status() == 401 || confirm.status() == 403,
                    () -> "停用工作人员确认请求应被拒：" + confirm.body());
            assertEquals(0L, countWhere(
                    "SELECT count(*) FROM change_confirmation WHERE change_id = ? AND user_id = ?",
                    UUID.fromString(changeId), USER_STAFF_A), "停用账号不得代签，change_confirmation 不应有该用户记录");
        } finally {
            HttpResult restored = call(HttpMethod.PATCH, "/api/v1/users/" + USER_STAFF_A + "/enabled",
                    admin, idem(), toJson(boolBody("enabled", true)));
            assertEquals(200, restored.status(), () -> "恢复工作人员失败：" + restored.body());
        }
        assertTrue(queryBool("SELECT enabled FROM app_user WHERE id = ?", USER_STAFF_A), "测试末尾必须恢复 staff_a 启用");

        ObjectNode withdraw = MAPPER.createObjectNode();
        withdraw.put("reason", "工作人员停用，撤回后重建（测试数据）");
        HttpResult withdrawn = call(HttpMethod.POST, "/api/v1/changes/" + changeId + "/withdraw",
                ownerA, idem(), toJson(withdraw));
        assertEquals(200, withdrawn.status(), () -> "撤回失败：" + withdrawn.body());
        assertEquals("WITHDRAWN", withdrawn.data().path("status").asText());

        int rebuiltBase = readActivityVersion(ownerA, ACTIVITY_A.toString());
        int rebuiltLock = readActivityLockVersion(ownerA, ACTIVITY_A.toString());
        ObjectNode rebuilt = changeCreateBody(rebuiltBase, "2030-10-20T12:30:00+08:00", "2030-10-20T13:30:00+08:00",
                30, 30, VENUE_3102, EQUIP_3202, 1, rebuiltLock);
        HttpResult recreated = call(HttpMethod.POST, "/api/v1/activities/" + ACTIVITY_A + "/changes",
                ownerA, idem(), toJson(rebuilt));
        assertEquals(201, recreated.status(), () -> "撤回后应可重建改期：" + recreated.body());
        String rebuiltId = recreated.data().path("id").asText();
        HttpResult withdrawnAgain = call(HttpMethod.POST, "/api/v1/changes/" + rebuiltId + "/withdraw",
                ownerA, idem(), toJson(withdraw));
        assertEquals(200, withdrawnAgain.status(), () -> "重建改期应可撤回：" + withdrawnAgain.body());
    }

    // ---------------------------------------------------------------- N-AT-02 支撑

    private void verifyCombo(Combo combo, String ownerToken, String approverToken, String resourceToken) {
        boolean teacher = "TEACHER".equals(combo.identity());
        boolean dbEligible = teacher
                ? queryBool("SELECT allow_teacher FROM resource WHERE id = ?", combo.venue())
                        && queryBool("SELECT allow_teacher FROM resource WHERE id = ?", combo.equip())
                : queryBool("SELECT allow_student FROM resource WHERE id = ?", combo.venue())
                        && queryBool("SELECT allow_student FROM resource WHERE id = ?", combo.equip());
        assertEquals(combo.eligible(), dbEligible, "demo 资源资格与 N-AT-02 组合不符：" + combo.label());

        ObjectNode input = activityInput("N-AT-02 " + combo.label(), 10, 5, List.of(),
                planNode(time(combo.startHour(), combo.startMinute()), time(combo.startHour() + 1, combo.startMinute()),
                        0, 0, combo.venue(), combo.equip(), 1));
        HttpResult created = call(HttpMethod.POST, "/api/v1/activities", ownerToken, idem(), toJson(input));

        if (created.status() == 403) {
            assertFalse(combo.eligible(), "资格允许的组合不应在创建阶段被拒：" + combo.label());
            assertIneligible(created, combo.identity(), combo.venue(), combo.equip());
            return;
        }
        assertEquals(201, created.status(), () -> combo.label() + " 创建活动失败：" + created.body());
        String activityId = created.data().path("id").asText();
        UUID activityUuid = UUID.fromString(activityId);

        HttpResult submitted = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/submit",
                ownerToken, idem(), toJson(versionCommand(created.data().path("lockVersion").asInt())));

        if (!combo.eligible()) {
            if (submitted.status() == 403) {
                assertIneligible(submitted, combo.identity(), combo.venue(), combo.equip());
                assertNoActiveReservation(activityUuid, combo.label());
                return;
            }
            assertEquals(200, submitted.status(), () -> combo.label() + " 提交失败：" + submitted.body());
            HttpResult approved = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/approver-decision",
                    approverToken, idem(), toJson(decisionBody("APPROVE", "",
                            submitted.data().path("lockVersion").asInt())));
            assertEquals(200, approved.status(), () -> combo.label() + " 审批失败：" + approved.body());
            HttpResult confirmed = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/resource-confirm",
                    resourceToken, idem(), toJson(versionCommand(approved.data().path("lockVersion").asInt())));
            assertIneligible(confirmed, combo.identity(), combo.venue(), combo.equip());
            assertNoActiveReservation(activityUuid, combo.label());
            return;
        }

        assertEquals(200, submitted.status(), () -> combo.label() + " 提交失败：" + submitted.body());
        HttpResult approved = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/approver-decision",
                approverToken, idem(),
                toJson(decisionBody("APPROVE", "", submitted.data().path("lockVersion").asInt())));
        assertEquals(200, approved.status(), () -> combo.label() + " 审批失败：" + approved.body());
        HttpResult confirmed = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/resource-confirm",
                resourceToken, idem(), toJson(versionCommand(approved.data().path("lockVersion").asInt())));
        assertEquals(200, confirmed.status(), () -> combo.label() + " 资源确认失败：" + confirmed.body());

        assertTrue(countWhere("SELECT count(*) FROM reservation WHERE activity_id = ? AND resource_id = ?",
                activityUuid, combo.venue()) >= 1, () -> combo.label() + " 应建立场地预约");
        assertTrue(countWhere("SELECT count(*) FROM reservation WHERE activity_id = ? AND resource_id = ?",
                activityUuid, combo.equip()) >= 1, () -> combo.label() + " 应建立设备预约");
    }

    private void assertNoActiveReservation(UUID activityId, String label) {
        assertEquals(0L, countWhere(
                "SELECT count(*) FROM reservation WHERE activity_id = ? AND status = 'ACTIVE'", activityId),
                label + " 资格不足不得写预约");
    }

    private void assertIneligible(HttpResult result, String identity, UUID... resources) {
        assertEquals(403, result.status(), () -> "资格不足应返回 403：" + result.body());
        assertEquals("RESOURCE_INELIGIBLE", result.errorCode(), () -> "资格不足错误码：" + result.body());
        JsonNode details = result.error().path("details");
        assertFalse(details.isMissingNode(), "RESOURCE_INELIGIBLE 必须带 details");
        String text = details.toString();
        boolean containsRestricted = false;
        for (UUID resource : resources) {
            if (text.contains(resource.toString())) {
                containsRestricted = true;
            }
        }
        assertTrue(containsRestricted, () -> "details 应含受限资源 ID：" + text);
        assertTrue(text.contains(identity), () -> "details 应含身份 " + identity + "：" + text);
    }

    // ---------------------------------------------------------------- 流程支撑

    private String createSubmittedActivity(String ownerToken, String title, String startAt, String endAt,
                                           int setupMinutes, int teardownMinutes, UUID venueId,
                                           UUID equipmentId, int equipmentQuantity,
                                           int expectedAttendees, int registrationCapacity) {
        ObjectNode input = activityInput(title, expectedAttendees, registrationCapacity, List.of(),
                planNode(startAt, endAt, setupMinutes, teardownMinutes, venueId, equipmentId, equipmentQuantity));
        HttpResult created = call(HttpMethod.POST, "/api/v1/activities", ownerToken, idem(), toJson(input));
        assertEquals(201, created.status(), () -> "创建活动失败：" + created.body());
        String activityId = created.data().path("id").asText();

        HttpResult submitted = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/submit",
                ownerToken, idem(), toJson(versionCommand(created.data().path("lockVersion").asInt())));
        assertEquals(200, submitted.status(), () -> "提交活动失败：" + submitted.body());
        return activityId;
    }

    private String createUser(String adminToken, String username, String displayName, UUID organizationId,
                              String identity, List<String> roles, List<String> permissions,
                              List<ObjectNode> scopes, List<UUID> managedResourceIds) {
        ObjectNode body = userCreateBody(username, displayName, organizationId, identity, roles, permissions,
                scopes, managedResourceIds);
        HttpResult created = call(HttpMethod.POST, "/api/v1/users", adminToken, idem(), toJson(body));
        assertEquals(201, created.status(), () -> "API-05 创建用户失败：" + created.body());
        return created.data().path("id").asText();
    }

    private void updateIdentity(String adminToken, UUID userId, String identity) {
        ObjectNode body = authorizationBody(identity, List.of("APPLICANT"), List.of(), List.of(), List.of());
        HttpResult result = call(HttpMethod.PATCH, "/api/v1/users/" + userId + "/authorization",
                adminToken, idem(), toJson(body));
        assertEquals(200, result.status(), () -> "API-39 修改授权失败：" + result.body());
        assertTrue(result.data().path("identity").asText().equals(identity)
                        || result.data().path("roles").isArray(),
                "API-39 应返回更新后的 Actor：" + result.body());
    }

    private List<String> activeResourceIds(UUID activityId) {
        return db.jdbc().queryForList(
                "SELECT DISTINCT resource_id::text FROM reservation WHERE activity_id = ? AND status = 'ACTIVE' ORDER BY resource_id::text",
                String.class, activityId);
    }

    private int readActivityVersion(String token, String activityId) {
        return readActivityFieldInt(token, activityId, "versionNo");
    }

    private int readActivityLockVersion(String token, String activityId) {
        return readActivityFieldInt(token, activityId, "lockVersion");
    }

    private int readActivityFieldInt(String token, String activityId, String field) {
        HttpResult result = call(HttpMethod.GET, "/api/v1/activities/" + activityId, token, null, null);
        assertEquals(200, result.status(), () -> "读取活动失败：" + result.body());
        return result.data().path(field).asInt();
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
        return loginWith(username, demoPassword());
    }

    private String loginWith(String username, String password) {
        HttpResult result = call(HttpMethod.POST, "/api/v1/auth/login", null, null,
                toJson(loginBody(username, password)));
        assertEquals(200, result.status(), () -> username + " 登录失败：" + result.body());
        return result.data().path("token").asText();
    }

    // ---------------------------------------------------------------- 落库辅助

    private long countWhere(String sql, Object... args) {
        Long value = db.jdbc().queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private boolean queryBool(String sql, Object... args) {
        Boolean value = db.jdbc().queryForObject(sql, Boolean.class, args);
        return Boolean.TRUE.equals(value);
    }

    private String queryString(String sql, Object... args) {
        return db.jdbc().queryForObject(sql, String.class, args);
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

    private static ObjectNode decisionBody(String decision, String comment) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("decision", decision);
        node.put("comment", comment);
        return node;
    }

    private static ObjectNode boolBody(String name, boolean value) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put(name, value);
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
        node.put("reason", "改期到更合适时段（测试数据）");
        return node;
    }

    private static ObjectNode scope(UUID organizationId, List<String> resourceCategories) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("organizationId", organizationId.toString());
        ArrayNode categories = node.putArray("resourceCategories");
        for (String category : resourceCategories) {
            categories.add(category);
        }
        return node;
    }

    private static ObjectNode authorizationBody(String identity, List<String> roles, List<String> permissions,
                                                List<ObjectNode> approvalScopes, List<UUID> managedResourceIds) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("identity", identity);
        ArrayNode roleArray = node.putArray("roles");
        for (String role : roles) {
            roleArray.add(role);
        }
        ArrayNode permissionArray = node.putArray("permissions");
        for (String permission : permissions) {
            permissionArray.add(permission);
        }
        ArrayNode scopeArray = node.putArray("approvalScopes");
        for (ObjectNode entry : approvalScopes) {
            scopeArray.add(entry);
        }
        ArrayNode managedArray = node.putArray("managedResourceIds");
        for (UUID resourceId : managedResourceIds) {
            managedArray.add(resourceId.toString());
        }
        return node;
    }

    private static ObjectNode userCreateBody(String username, String displayName, UUID organizationId,
                                             String identity, List<String> roles, List<String> permissions,
                                             List<ObjectNode> approvalScopes, List<UUID> managedResourceIds) {
        ObjectNode node = authorizationBody(identity, roles, permissions, approvalScopes, managedResourceIds);
        node.put("username", username);
        node.put("displayName", displayName);
        node.put("password", NEW_USER_PASSWORD);
        node.put("organizationId", organizationId.toString());
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

    private static String demoPassword() {
        String value = System.getenv("DEMO_PASSWORD");
        assertNotNull(value, "缺少 DEMO_PASSWORD 环境变量（03 §1/§3）");
        assertFalse(value.isBlank(), "DEMO_PASSWORD 不能为空");
        return value;
    }

    private static String idem() {
        return UUID.randomUUID().toString();
    }

    private record Combo(String label, String identity, UUID venue, UUID equip, boolean eligible,
                         int startHour, int startMinute) {
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
