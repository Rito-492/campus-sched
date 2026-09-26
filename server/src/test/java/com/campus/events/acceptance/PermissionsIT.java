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
import org.springframework.web.util.UriComponentsBuilder;

import java.time.OffsetDateTime;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * D-AT-01 / D-AT-02 / D-AT-08 / D-AT-20 / D-AT-21 集成验收测试（Permissions）。
 *
 * <p>对应设计：{@code docs/testing/test-design.md} 的 TC-01-1/2/3、TC-02-1/2/3、TC-08-1/2、
 * TC-20-1/2、TC-21-1/2/3。断言依据 02 数据与接口契约（字段名、错误码、响应包装、幂等键规则）
 * 与 01 开发基线（状态机、占用区间）。
 *
 * <p>装载数据：{@code test-data/demo.json}（固定 Clock=2030-10-19T00:00:00Z、DEMO_DATE=2030-10-20）。
 * 除演示活动 A/B 外，本类通过 HTTP 新建临时活动验证幂等与陈旧版本保护，互不干扰。
 *
 * <p>执行命令（在仓库根目录）：
 * {@code ./server/mvnw.cmd -f server/pom.xml "-Dit.test=PermissionsIT" verify}
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@Import(TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("D-AT-01/02/08/20/21 权限、幂等、陈旧版本与读取隔离（PermissionsIT）")
class PermissionsIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final UUID ORG_1001 = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID USER_ORGANIZER_A = UUID.fromString("00000000-0000-0000-0000-000000002001");
    private static final UUID USER_ORGANIZER_B = UUID.fromString("00000000-0000-0000-0000-000000002002");
    private static final UUID USER_TEACHER_A = UUID.fromString("00000000-0000-0000-0000-000000002003");
    private static final UUID USER_RESOURCE_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000002004");
    private static final UUID USER_STUDENT_A = UUID.fromString("00000000-0000-0000-0000-000000002005");
    private static final UUID USER_STAFF_A = UUID.fromString("00000000-0000-0000-0000-000000002006");
    private static final UUID USER_SYS_ADMIN = UUID.fromString("00000000-0000-0000-0000-000000002007");
    private static final UUID USER_TEACHER_OTHER = UUID.fromString("00000000-0000-0000-0000-000000002008");
    private static final UUID USER_STUDENT_B = UUID.fromString("00000000-0000-0000-0000-000000002009");

    private static final UUID ACTIVITY_A = UUID.fromString("00000000-0000-0000-0000-000000004001");
    private static final UUID ACTIVITY_B = UUID.fromString("00000000-0000-0000-0000-000000004002");

    private static final UUID VENUE_3101 = UUID.fromString("00000000-0000-0000-0000-000000003101");
    private static final UUID VENUE_3102 = UUID.fromString("00000000-0000-0000-0000-000000003102");
    private static final UUID EQUIP_3201 = UUID.fromString("00000000-0000-0000-0000-000000003201");
    private static final UUID EQUIP_3202 = UUID.fromString("00000000-0000-0000-0000-000000003202");

    @RegisterExtension
    static final DbTestSupport db = DbTestSupport.create("demo");

    @Autowired
    private TestRestTemplate rest;

    // ------------------------------------------------------------------ D-AT-01

    @Test
    @DisplayName("D-AT-01 / TC-01-1 正常登录并访问，响应任意层级无口令字段")
    void tc0101_loginAndMeAreRedacted() {
        long sessionsBefore = countWhere("SELECT count(*) FROM auth_session");

        HttpResult login = call(HttpMethod.POST, "/api/v1/auth/login", null, null,
                toJson(loginBody("organizer_a", demoPassword())));
        assertEquals(200, login.status(), () -> "登录失败：" + login.body());
        assertTrue(login.body().hasNonNull("requestId"));
        JsonNode data = login.data();
        String token = data.path("token").asText();
        assertFalse(token.isBlank(), "登录响应缺少 token");
        assertDoesNotThrow(() -> OffsetDateTime.parse(data.path("expiresAt").asText()),
                "expiresAt 应为带时区 ISO8601");
        JsonNode user = data.path("user");
        assertEquals(USER_ORGANIZER_A.toString(), user.path("id").asText());
        assertEquals(ORG_1001.toString(), user.path("organizationId").asText());
        assertTrue(rolesContain(user.path("roles"), "ORGANIZER"));
        assertNoSecretKeys(login.body());

        assertEquals(sessionsBefore + 1, countWhere("SELECT count(*) FROM auth_session"));
        assertEquals(0L, countWhere("SELECT count(*) FROM auth_session WHERE token_hash = ?", token),
                "token_hash 不得保存 token 明文");

        HttpResult me = call(HttpMethod.GET, "/api/v1/auth/me", token, null, null);
        assertEquals(200, me.status(), () -> "/auth/me 失败：" + me.body());
        assertEquals(USER_ORGANIZER_A.toString(), me.data().path("id").asText());
        assertEquals(ORG_1001.toString(), me.data().path("organizationId").asText());
        assertTrue(rolesContain(me.data().path("roles"), "ORGANIZER"));
        assertNoSecretKeys(me.body());
    }

    @Test
    @DisplayName("D-AT-01 / TC-01-2 错误密码 401 且账号不锁死")
    void tc0102_wrongPasswordUnauthenticated() {
        long sessionsBefore = countWhere("SELECT count(*) FROM auth_session");

        HttpResult bad = call(HttpMethod.POST, "/api/v1/auth/login", null, null,
                toJson(loginBody("organizer_a", "wrong-password-" + UUID.randomUUID())));
        assertEquals(401, bad.status());
        assertEquals("UNAUTHENTICATED", bad.errorCode());
        assertTrue(bad.body().hasNonNull("requestId"));
        JsonNode error = bad.body().path("error");
        assertTrue(error.hasNonNull("code"));
        assertTrue(error.has("message"));
        assertTrue(error.has("details"));
        assertNoSecretKeys(bad.body());
        assertEquals(sessionsBefore, countWhere("SELECT count(*) FROM auth_session"),
                "失败登录不应产生会话行");

        HttpResult good = call(HttpMethod.POST, "/api/v1/auth/login", null, null,
                toJson(loginBody("organizer_a", demoPassword())));
        assertEquals(200, good.status(), "账号不应被锁死，正确密码仍可登录");
        assertFalse(good.data().path("token").asText().isBlank());
    }

    @Test
    @DisplayName("D-AT-01 / TC-01-3 停用账号使旧令牌失效，末尾恢复启用")
    void tc0103_disabledAccountInvalidatesOldToken() {
        String sysToken = login("sys_admin");
        String studentToken = login("student_a");

        HttpResult disabled = call(HttpMethod.PATCH, "/api/v1/users/" + USER_STUDENT_A + "/enabled",
                sysToken, idem(), toJson(boolBody("enabled", false)));
        assertEquals(200, disabled.status(), () -> "停用失败：" + disabled.body());
        assertTrue(disabled.data().path("ok").asBoolean());
        assertFalse(queryBool("SELECT enabled FROM app_user WHERE id = ?", USER_STUDENT_A));

        try {
            HttpResult me = call(HttpMethod.GET, "/api/v1/auth/me", studentToken, null, null);
            assertEquals(401, me.status(), "停用后旧令牌必须 401");
        } finally {
            HttpResult restored = call(HttpMethod.PATCH, "/api/v1/users/" + USER_STUDENT_A + "/enabled",
                    sysToken, idem(), toJson(boolBody("enabled", true)));
            assertEquals(200, restored.status());
        }
        assertTrue(queryBool("SELECT enabled FROM app_user WHERE id = ?", USER_STUDENT_A),
                "测试末尾必须恢复 student_a 启用");
    }

    // ------------------------------------------------------------------ D-AT-02

    @Test
    @DisplayName("D-AT-02 / TC-02-1 外院教师审批本组织活动 403")
    void tc0201_teacherFromOtherOrgCannotDecide() {
        String owner = login("organizer_a");
        String teacherOther = login("teacher_other");
        ActivitySnapshot before = snapshotA();
        int lockVersion = readActivityLockVersion(owner, ACTIVITY_A.toString());

        HttpResult r = call(HttpMethod.POST, "/api/v1/activities/" + ACTIVITY_A + "/teacher-decision",
                teacherOther, idem(), toJson(decisionBody("APPROVE", "", lockVersion)));
        assertEquals(403, r.status());
        assertEquals("FORBIDDEN", r.errorCode());
        assertAUnchanged(before);
    }

    @Test
    @DisplayName("D-AT-02 / TC-02-2 SYS_ADMIN 无业务审批权 403")
    void tc0202_sysAdminHasNoBusinessApproval() {
        String owner = login("organizer_a");
        String sysToken = login("sys_admin");
        ActivitySnapshot before = snapshotA();
        int lockVersion = readActivityLockVersion(owner, ACTIVITY_A.toString());

        HttpResult r = call(HttpMethod.POST, "/api/v1/activities/" + ACTIVITY_A + "/teacher-decision",
                sysToken, idem(), toJson(decisionBody("APPROVE", "", lockVersion)));
        assertEquals(403, r.status());
        assertEquals("FORBIDDEN", r.errorCode());
        assertAUnchanged(before);
    }

    @Test
    @DisplayName("D-AT-02 / TC-02-3 非所有者编辑活动 403")
    void tc0203_nonOwnerCannotEdit() {
        String owner = login("organizer_a");
        String organizerB = login("organizer_b");
        ActivitySnapshot before = snapshotA();
        int lockVersion = readActivityLockVersion(owner, ACTIVITY_A.toString());

        ObjectNode edit = MAPPER.createObjectNode();
        edit.put("expectedLockVersion", lockVersion);
        edit.set("input", activityInput("校园技术分享会（越权编辑）", 60, 20, List.of(USER_STAFF_A),
                planNode("2030-10-20T10:00:00+08:00", "2030-10-20T11:00:00+08:00",
                        30, 30, VENUE_3101, EQUIP_3201, 2)));
        HttpResult r = call(HttpMethod.PATCH, "/api/v1/activities/" + ACTIVITY_A,
                organizerB, idem(), toJson(edit));
        assertEquals(403, r.status());
        assertEquals("FORBIDDEN", r.errorCode());
        assertAUnchanged(before);
    }

    // ------------------------------------------------------------------ D-AT-08

    @Test
    @DisplayName("D-AT-08 / TC-08-1 同 key 同 body 重复资源确认不新增记录")
    void tc0801_replaySameKeySameBodyReturnsFirstResponse() {
        String owner = login("organizer_a");
        String resource = login("resource_admin");
        String activityId = createApprovedUnreserved(owner, "幂等确认活动（测试数据）",
                "2030-10-20T13:00:00+08:00", "2030-10-20T14:00:00+08:00",
                0, 0, VENUE_3101, EQUIP_3201, 1, 5, 5);
        int lockVersion = readActivityLockVersion(owner, activityId);
        String key = idem();
        String body = toJson(versionCommand(lockVersion));

        long receiptsBefore = countWhere(
                "SELECT count(*) FROM command_receipt WHERE actor_id = ? AND idempotency_key = ?",
                USER_RESOURCE_ADMIN, key);
        long reservationsBefore = countWhere("SELECT count(*) FROM reservation WHERE activity_id = ?",
                UUID.fromString(activityId));

        HttpResult first = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/resource-confirm",
                resource, key, body);
        assertEquals(200, first.status(), () -> "首次确认失败：" + first.body());
        assertEquals("SCHEDULED", first.data().path("status").asText());

        long receiptsAfterFirst = countWhere(
                "SELECT count(*) FROM command_receipt WHERE actor_id = ? AND idempotency_key = ?",
                USER_RESOURCE_ADMIN, key);
        long reservationsAfterFirst = countWhere("SELECT count(*) FROM reservation WHERE activity_id = ?",
                UUID.fromString(activityId));
        assertEquals(receiptsBefore + 1, receiptsAfterFirst, "确认成功应写入 1 条 command_receipt");
        assertEquals(reservationsBefore + 2, reservationsAfterFirst,
                "场地 1 行 + 器材 1 行 = 2 条 ACTIVE 预约");

        HttpResult replay = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/resource-confirm",
                resource, key, body);
        assertEquals(200, replay.status(), () -> "重放失败：" + replay.body());
        assertEquals(first.data(), replay.data(), "重放应返回首次实体");
        assertEquals(first.body().path("requestId").asText(), replay.body().path("requestId").asText(),
                "重放应保留首次 requestId");
        assertEquals(receiptsAfterFirst, countWhere(
                        "SELECT count(*) FROM command_receipt WHERE actor_id = ? AND idempotency_key = ?",
                        USER_RESOURCE_ADMIN, key),
                "重放不得新增 command_receipt");
        assertEquals(reservationsAfterFirst, countWhere("SELECT count(*) FROM reservation WHERE activity_id = ?",
                        UUID.fromString(activityId)),
                "重放不得新增 reservation");
    }

    @Test
    @DisplayName("D-AT-08 / TC-08-2 同 key 不同 body 409 IDEMPOTENCY_KEY_REUSED")
    void tc0802_sameKeyDifferentBodyRejected() {
        String owner = login("organizer_a");
        String resource = login("resource_admin");
        String activityId = createApprovedUnreserved(owner, "幂等冲突活动（测试数据）",
                "2030-10-20T13:00:00+08:00", "2030-10-20T14:00:00+08:00",
                0, 0, VENUE_3102, EQUIP_3202, 1, 5, 5);
        int lockVersion = readActivityLockVersion(owner, activityId);
        String key = idem();

        HttpResult first = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/resource-confirm",
                resource, key, toJson(versionCommand(lockVersion)));
        assertEquals(200, first.status(), () -> "首次确认失败：" + first.body());

        long reservationsAfterFirst = countWhere("SELECT count(*) FROM reservation WHERE activity_id = ?",
                UUID.fromString(activityId));
        HttpResult reused = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/resource-confirm",
                resource, key, toJson(versionCommand(lockVersion + 1)));
        assertEquals(409, reused.status());
        assertEquals("IDEMPOTENCY_KEY_REUSED", reused.errorCode());
        assertEquals(1L, countWhere(
                        "SELECT count(*) FROM command_receipt WHERE actor_id = ? AND idempotency_key = ?",
                        USER_RESOURCE_ADMIN, key),
                "键只能对应 1 条 command_receipt");
        assertEquals(reservationsAfterFirst, countWhere("SELECT count(*) FROM reservation WHERE activity_id = ?",
                        UUID.fromString(activityId)),
                "键复用不得执行新业务写入");
    }

    // ------------------------------------------------------------------ D-AT-20

    @Test
    @DisplayName("D-AT-20 / TC-20-1/2 旧 baseVersionNo 与旧 expectedLockVersion 均 409 STALE_VERSION")
    void tc2001And02_staleVersionProtection() {
        String owner = login("organizer_a");

        ObjectNode inputV1 = activityInput("陈旧版本保护活动（测试数据）", 5, 5, List.of(),
                planNode("2030-10-20T17:00:00+08:00", "2030-10-20T18:00:00+08:00",
                        0, 0, VENUE_3102, EQUIP_3202, 1));
        HttpResult created = call(HttpMethod.POST, "/api/v1/activities", owner, idem(), toJson(inputV1));
        assertEquals(201, created.status(), () -> "创建活动失败：" + created.body());
        String activityId = created.data().path("id").asText();
        int lockVersion = created.data().path("lockVersion").asInt();

        ObjectNode edit = MAPPER.createObjectNode();
        edit.put("expectedLockVersion", lockVersion);
        edit.set("input", activityInput("陈旧版本保护活动（第二版）", 5, 5, List.of(),
                planNode("2030-10-20T17:00:00+08:00", "2030-10-20T18:00:00+08:00",
                        0, 0, VENUE_3102, EQUIP_3202, 1)));
        HttpResult edited = call(HttpMethod.PATCH, "/api/v1/activities/" + activityId, owner, idem(), toJson(edit));
        assertEquals(200, edited.status(), () -> "DRAFT 编辑失败：" + edited.body());
        assertEquals(2, edited.data().path("versionNo").asInt());

        HttpResult submitted = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/submit",
                owner, idem(), toJson(versionCommand(edited.data().path("lockVersion").asInt())));
        assertEquals(200, submitted.status(), () -> "提交失败：" + submitted.body());

        String teacher = login("teacher_a");
        HttpResult approved = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/teacher-decision",
                teacher, idem(), toJson(decisionBody("APPROVE", "", submitted.data().path("lockVersion").asInt())));
        assertEquals(200, approved.status(), () -> "教师审批失败：" + approved.body());

        String resource = login("resource_admin");
        int confirmLock = readActivityLockVersion(owner, activityId);
        HttpResult confirmed = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/resource-confirm",
                resource, idem(), toJson(versionCommand(confirmLock)));
        assertEquals(200, confirmed.status(), () -> "资源确认失败：" + confirmed.body());
        HttpResult published = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/publish",
                owner, idem(), toJson(versionCommand(confirmed.data().path("lockVersion").asInt())));
        assertEquals(200, published.status(), () -> "发布失败：" + published.body());
        assertEquals("OPEN", published.data().path("status").asText());

        int currentVersion = readActivityVersion(owner, activityId);
        int currentLock = readActivityLockVersion(owner, activityId);
        long versionsBefore = countWhere("SELECT count(*) FROM activity_version WHERE activity_id = ?",
                UUID.fromString(activityId));

        ObjectNode staleBase = changeCreateBody(1, "2030-10-20T18:00:00+08:00", "2030-10-20T19:00:00+08:00",
                30, 30, VENUE_3102, EQUIP_3202, 1, currentLock);
        HttpResult r1 = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/changes",
                owner, idem(), toJson(staleBase));
        assertEquals(409, r1.status());
        assertEquals("STALE_VERSION", r1.errorCode());
        assertTrue(r1.body().path("error").path("details").has("versionNo")
                        || r1.body().path("error").path("details").has("lockVersion"),
                "STALE_VERSION details 应含当前 versionNo/lockVersion");

        ObjectNode staleLock = changeCreateBody(currentVersion,
                "2030-10-20T18:00:00+08:00", "2030-10-20T19:00:00+08:00",
                30, 30, VENUE_3102, EQUIP_3202, 1, 0);
        HttpResult r2 = call(HttpMethod.POST, "/api/v1/activities/" + activityId + "/changes",
                owner, idem(), toJson(staleLock));
        assertEquals(409, r2.status());
        assertEquals("STALE_VERSION", r2.errorCode());

        assertEquals(versionsBefore, countWhere("SELECT count(*) FROM activity_version WHERE activity_id = ?",
                UUID.fromString(activityId)), "陈旧版本请求不得产生新 activity_version");
    }

    // ------------------------------------------------------------------ D-AT-21

    @Test
    @DisplayName("D-AT-21 / TC-21-1 学生日历脱敏，不泄露他人活动标题")
    void tc2101_calendarRedactsUnauthorizedActivityTitles() {
        String student = login("student_a");
        String url = UriComponentsBuilder.fromPath("/api/v1/resources/calendar")
                .queryParam("from", "2030-10-20T00:00:00+08:00")
                .queryParam("to", "2030-10-21T00:00:00+08:00")
                .build().encode().toUriString();

        HttpResult r = call(HttpMethod.GET, url, student, null, null);
        assertEquals(200, r.status(), () -> "日历读取失败：" + r.body());
        JsonNode entries = r.data();
        assertTrue(entries.isArray(), "data 应为数组");
        assertTrue(r.body().has("truncated"), "列表响应应含 truncated");

        boolean sawVenueB = false;
        for (JsonNode entry : entries) {
            assertFalse(entry.has("ownerId"), "日历条目不得含 ownerId");
            assertFalse(entry.has("registeredUserIds"), "日历条目不得含报名名单");
            assertFalse(entry.has("staffUserIds"), "日历条目不得含工作人员名单");
            assertFalse(entry.path("label").asText().contains("社团交流会"),
                    "无权查看活动的标题不得出现在日历 label：" + entry);
            if (VENUE_3101.toString().equals(entry.path("resourceId").asText())) {
                if (overlaps(entry.path("startsAt").asText(), entry.path("endsAt").asText(),
                        "2030-10-20T14:30:00+08:00", "2030-10-20T16:30:00+08:00")) {
                    sawVenueB = true;
                }
            }
        }
        assertTrue(sawVenueB, "日历应含 3101 在 14:30-16:30 的无权限占用条目（脱敏后仍显示占用）");
    }

    @Test
    @DisplayName("D-AT-21 / TC-21-2 学生读取他人活动名单 403")
    void tc2102_studentCannotReadForeignRegistrations() {
        String student = login("student_a");
        HttpResult r = call(HttpMethod.GET, "/api/v1/activities/" + ACTIVITY_B + "/registrations",
                student, null, null);
        assertEquals(403, r.status());
        assertEquals("FORBIDDEN", r.errorCode());
        assertFalse(r.body().toString().contains(USER_STUDENT_A.toString()),
                "403 响应不得泄露报名人信息");
    }

    @Test
    @DisplayName("D-AT-21 / TC-21-3 工作人员读取变更详情名单裁剪但计数真实")
    void tc2103_changeViewRedactedForStaff() {
        String owner = login("organizer_a");
        String staff = login("staff_a");
        int version = readActivityVersion(owner, ACTIVITY_A.toString());
        int lockVersion = readActivityLockVersion(owner, ACTIVITY_A.toString());

        ObjectNode change = changeCreateBody(version, "2030-10-20T18:00:00+08:00", "2030-10-20T19:00:00+08:00",
                30, 30, VENUE_3102, EQUIP_3202, 2, lockVersion);
        HttpResult created = call(HttpMethod.POST, "/api/v1/activities/" + ACTIVITY_A + "/changes",
                owner, idem(), toJson(change));
        assertEquals(201, created.status(), () -> "创建改期失败：" + created.body());
        String changeId = created.data().path("id").asText();

        HttpResult staffView = call(HttpMethod.GET, "/api/v1/changes/" + changeId, staff, null, null);
        assertEquals(200, staffView.status(), () -> "工作人员读取改期失败：" + staffView.body());
        JsonNode staffImpact = staffView.data().path("impact");
        assertEquals(0, staffImpact.path("registeredUserIds").size(), "工作人员视角 registeredUserIds 应为空数组");
        assertEquals(0, staffImpact.path("notificationUserIds").size(), "工作人员视角 notificationUserIds 应为空数组");
        assertTrue(staffImpact.path("registeredCount").asInt() > 0, "registeredCount 应保持真实");

        HttpResult ownerView = call(HttpMethod.GET, "/api/v1/changes/" + changeId, owner, null, null);
        assertEquals(200, ownerView.status());
        JsonNode ownerImpact = ownerView.data().path("impact");
        assertTrue(ownerImpact.path("registeredUserIds").size() > 0, "所有者视角 registeredUserIds 应非空");
        assertTrue(ownerImpact.path("notificationUserIds").size() > 0, "所有者视角 notificationUserIds 应非空");
        assertEquals(staffImpact.path("registeredCount").asInt(), ownerImpact.path("registeredCount").asInt());
    }

    // ------------------------------------------------------------------ 支撑

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

    private ActivitySnapshot snapshotA() {
        return new ActivitySnapshot(
                db.activityStatus(ACTIVITY_A),
                queryInt("SELECT lock_version FROM activity WHERE id = ?", ACTIVITY_A),
                countWhere("SELECT count(*) FROM activity_approval WHERE activity_id = ?", ACTIVITY_A),
                countWhere("SELECT count(*) FROM reservation WHERE activity_id = ? AND status = 'ACTIVE'",
                        ACTIVITY_A));
    }

    private void assertAUnchanged(ActivitySnapshot before) {
        assertEquals(before.status(), db.activityStatus(ACTIVITY_A), "活动 A 状态不得变化");
        assertEquals(before.lockVersion(),
                queryInt("SELECT lock_version FROM activity WHERE id = ?", ACTIVITY_A), "lock_version 不得变化");
        assertEquals(before.approvals(),
                countWhere("SELECT count(*) FROM activity_approval WHERE activity_id = ?", ACTIVITY_A),
                "activity_approval 不得新增");
        assertEquals(before.activeReservations(),
                countWhere("SELECT count(*) FROM reservation WHERE activity_id = ? AND status = 'ACTIVE'",
                        ACTIVITY_A),
                "ACTIVE 预约计数不得变化");
    }

    private long countWhere(String sql, Object... args) {
        Long value = db.jdbc().queryForObject(sql, Long.class, args);
        return value == null ? 0L : value;
    }

    private int queryInt(String sql, Object... args) {
        Integer value = db.jdbc().queryForObject(sql, Integer.class, args);
        return value == null ? 0 : value;
    }

    private boolean queryBool(String sql, Object... args) {
        Boolean value = db.jdbc().queryForObject(sql, Boolean.class, args);
        return Boolean.TRUE.equals(value);
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

    private static boolean overlaps(String aStart, String aEnd, String bStart, String bEnd) {
        return OffsetDateTime.parse(aStart).toInstant().isBefore(OffsetDateTime.parse(bEnd).toInstant())
                && OffsetDateTime.parse(aEnd).toInstant().isAfter(OffsetDateTime.parse(bStart).toInstant());
    }

    private static boolean rolesContain(JsonNode roles, String role) {
        for (JsonNode node : roles) {
            if (role.equals(node.asText())) {
                return true;
            }
        }
        return false;
    }

    private static void assertNoSecretKeys(JsonNode node) {
        if (node == null || node.isNull()) {
            return;
        }
        if (node.isObject()) {
            Iterator<String> names = node.fieldNames();
            while (names.hasNext()) {
                String name = names.next();
                String lower = name.toLowerCase(Locale.ROOT);
                assertFalse("password".equals(lower) || "passwordhash".equals(lower),
                        "响应泄露口令字段：" + name);
                assertNoSecretKeys(node.get(name));
            }
        } else if (node.isArray()) {
            for (JsonNode child : node) {
                assertNoSecretKeys(child);
            }
        }
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

    private record ActivitySnapshot(String status, int lockVersion, long approvals, long activeReservations) {
    }
}
