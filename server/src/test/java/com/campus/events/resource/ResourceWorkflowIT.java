package com.campus.events.resource;

import com.campus.events.CampusEventsApplication;
import com.campus.events.contract.*;
import com.campus.events.notification.NotificationConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.scheduling.annotation.ScheduledAnnotationBeanPostProcessor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.junit.jupiter.api.Assertions.*;

/** DS 接收 AN/CM 后的真实 HTTP/数据库回归；全部业务端口和通知消费者均为生产实现。 */
@SpringBootTest(classes = CampusEventsApplication.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(ResourceWorkflowIT.FixedTime.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResourceWorkflowIT {
    static final String SCHEMA = "t_" + UUID.randomUUID().toString().replace("-", "");
    static final Instant NOW = Instant.parse("2030-10-19T00:00:00Z");
    static final Instant START = Instant.parse("2030-10-20T02:00:00Z");
    static String url, user, password;
    @Autowired JdbcTemplate jdbc;
    @Autowired MockMvc http;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @Autowired NotificationConsumer consumer;
    @Autowired ScheduledAnnotationBeanPostProcessor schedules;
    UUID org, owner, owner2, approver, admin, staff, student, venue, venue2, equipment, equipment2;
    String ownerToken, owner2Token, approverToken, adminToken, staffToken, studentToken;

    @TestConfiguration static class FixedTime {
        @Bean @Primary Clock dsWorkflowClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
    static String required(String key) {
        var value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("DS真实闭环缺少环境变量 " + key);
        return value;
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        url = required("TEST_DATABASE_URL"); user = required("TEST_DB_USERNAME"); password = required("TEST_DB_PASSWORD");
        required("DEMO_PASSWORD");
        if (!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/campus_test"))
            throw new IllegalStateException("DS闭环测试仅允许本机campus_test随机schema");
        new JdbcTemplate(new DriverManagerDataSource(url, user, password)).execute("CREATE SCHEMA " + SCHEMA);
        properties.add("spring.datasource.url", () -> url + "?currentSchema=" + SCHEMA);
        properties.add("spring.datasource.username", () -> user);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("app.demo-seed", () -> "false");
    }
    @BeforeAll void manualDelivery() {
        // 保留真实消费者，取消后台定时触发；断言前显式drain，不靠sleep等待通知。
        schedules.getScheduledTasks().forEach(task -> task.cancel());
    }
    @AfterAll void cleanup() {
        if (url != null && SCHEMA.matches("t_[0-9a-f]{32}"))
            new JdbcTemplate(new DriverManagerDataSource(url, user, password)).execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }
    @BeforeEach void seed() throws Exception {
        jdbc.execute("TRUNCATE organization,resource CASCADE");
        org = UUID.randomUUID();
        jdbc.update("INSERT INTO organization(id,name) VALUES (?, 'DS真实闭环组织')", org);
        var hash = encoder.encode(required("DEMO_PASSWORD"));
        owner = account(Identity.TEACHER, Role.APPLICANT, hash);
        owner2 = account(Identity.STUDENT, Role.APPLICANT, hash);
        approver = account(Identity.TEACHER, Role.APPROVER, hash);
        admin = account(Identity.TEACHER, Role.ADMIN, hash);
        staff = account(Identity.STUDENT, Role.APPLICANT, hash);
        student = account(Identity.STUDENT, Role.APPLICANT, hash);
        jdbc.update("INSERT INTO user_permission(user_id,permission) VALUES (?,'RESOURCE_MANAGE'),(?,'RESOURCE_CONFIRM'),(?,'AUDIT_READ')",
                admin, admin, admin);
        jdbc.update("INSERT INTO user_approval_scope(user_id,organization_id,resource_category) VALUES (?,?,'VENUE'),(?,?,'PROJECTOR')",
                approver, org, approver, org);
        ownerToken = login(owner); owner2Token = login(owner2); approverToken = login(approver);
        adminToken = login(admin); staffToken = login(staff); studentToken = login(student);
        venue = resource(ResourceKind.VENUE); venue2 = resource(ResourceKind.VENUE);
        equipment = resource(ResourceKind.EQUIPMENT); equipment2 = resource(ResourceKind.EQUIPMENT);
    }
    UUID account(Identity identity, Role role, String hash) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(id,organization_id,username,display_name,password_hash,identity) VALUES (?,?,?,?,?,?)",
                id, org, id.toString(), "DS闭环测试账号", hash, identity.name());
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,?)", id, role.name());
        return id;
    }
    UUID resource(ResourceKind kind) throws Exception {
        var result = write("POST", "/resources", adminToken, new ResourceCreate(true, true, kind,
                "DS闭环资源", kind == ResourceKind.VENUE ? "VENUE" : "PROJECTOR",
                kind == ResourceKind.VENUE ? 80 : null, kind == ResourceKind.VENUE ? 1 : 2, "08:00", "22:00"), 201);
        return UUID.fromString(result.path("id").asText());
    }
    record Response(int status, JsonNode body) { JsonNode data() { return body.path("data"); } }
    String login(UUID id) throws Exception {
        var result = call("POST", "/auth/login", null, null, new LoginInput(id.toString(), required("DEMO_PASSWORD")));
        assertEquals(200, result.status(), result.body().toString());
        return result.data().path("token").asText();
    }
    Response call(String method, String path, String token, UUID key, Object input) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), "/api/v1" + path)
                .contentType("application/json");
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        if (input != null) request.content(mapper.writeValueAsString(input));
        var response = http.perform(request).andReturn().getResponse();
        return new Response(response.getStatus(), mapper.readTree(response.getContentAsString()));
    }
    JsonNode write(String method, String path, String token, Object input, int expected) throws Exception {
        var response = call(method, path, token, UUID.randomUUID(), input);
        assertEquals(expected, response.status(), response.body().toString());
        assertTrue(response.body().hasNonNull("requestId"));
        return response.data();
    }
    JsonNode get(String path, String token) throws Exception {
        var response = call("GET", path, token, null, null);
        assertEquals(200, response.status(), response.body().toString());
        return response.data();
    }
    Plan plan(UUID place, UUID device, int shift, int duration, int setup, int quantity) {
        return new Plan(START.plusSeconds(shift * 60L), START.plusSeconds((shift + duration) * 60L),
                setup, setup, place, List.of(new EquipmentNeed(device, quantity)));
    }
    UUID approved(String token, Plan plan, boolean withStaff) throws Exception {
        var created = write("POST", "/activities", token,
                new ActivityInput("DS资源闭环", "真实端口回归", 60, 20, withStaff ? List.of(staff) : List.of(), plan), 201);
        UUID id = UUID.fromString(created.path("id").asText());
        var submitted = write("POST", "/activities/" + id + "/submit", token,
                new VersionCommand(created.path("lockVersion").asInt()), 200);
        var reviewed = write("POST", "/activities/" + id + "/approver-decision", approverToken,
                new ApprovalCommand(submitted.path("lockVersion").asInt(), Decision.APPROVE, "同意申请"), 200);
        assertEquals("APPROVED_UNRESERVED", reviewed.path("status").asText());
        return id;
    }
    JsonNode confirm(UUID id) throws Exception {
        var current = get("/activities/" + id, adminToken);
        return write("POST", "/activities/" + id + "/resource-confirm", adminToken,
                new VersionCommand(current.path("lockVersion").asInt()), 200);
    }
    UUID open(Plan original, boolean withStaff) throws Exception {
        UUID id = approved(ownerToken, original, withStaff);
        var booked = confirm(id);
        write("POST", "/activities/" + id + "/publish", ownerToken,
                new VersionCommand(booked.path("lockVersion").asInt()), 200);
        write("PUT", "/activities/" + id + "/registration", studentToken,
                new RegistrationCommand(RegistrationAction.JOIN, 1), 200);
        return id;
    }
    UUID readyChange(UUID id, Plan target, boolean withStaff) throws Exception {
        var current = get("/activities/" + id, ownerToken);
        var created = write("POST", "/activities/" + id + "/changes", ownerToken,
                new ChangeCreate(1, target, current.path("lockVersion").asInt(), "调整资源安排"), 201);
        UUID change = UUID.fromString(created.path("id").asText());
        assertEquals(withStaff ? "AWAITING_CONFIRMATIONS" : "PENDING_APPROVER", created.path("status").asText());
        if (withStaff) {
            var confirmed = write("POST", "/changes/" + change + "/confirmation", staffToken,
                    new ChangeDecision(Decision.APPROVE, "可按新安排参加"), 200);
            assertEquals("PENDING_APPROVER", confirmed.path("status").asText());
            assertTrue(confirmed.path("impact").path("registeredUserIds").isEmpty());
            assertTrue(confirmed.path("impact").path("notificationUserIds").isEmpty());
        }
        var reviewed = write("POST", "/changes/" + change + "/approver-decision", approverToken,
                new ChangeDecision(Decision.APPROVE, "同意改期"), 200);
        assertEquals("PENDING_RESOURCE", reviewed.path("status").asText());
        return change;
    }
    List<Map<String, Object>> reservations(UUID id) {
        return jdbc.queryForList("SELECT * FROM reservation WHERE activity_id=? ORDER BY id", id);
    }
    long count(String table, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE activity_id=?", Long.class, id);
    }
    void originalUnchanged(UUID id, List<Map<String, Object>> before) throws Exception {
        var current = get("/activities/" + id, ownerToken);
        assertEquals(1, current.path("versionNo").asInt());
        assertEquals(1, current.path("effectiveVersionNo").asInt());
        assertEquals(before, reservations(id));
        assertEquals(1, count("activity_version", id));
        assertEquals(1, jdbc.queryForObject("SELECT version_no FROM activity_registration WHERE activity_id=? AND user_id=?",
                Integer.class, id, student));
    }

    @Test void firstConfirmationReplaysSameKeyAndRejectsChangedRequestWithoutExtraWrites() throws Exception {
        UUID id = approved(ownerToken, plan(venue, equipment, 0, 60, 0, 2), false);
        var command = new VersionCommand(get("/activities/" + id, ownerToken).path("lockVersion").asInt());
        String route = "/activities/" + id + "/resource-confirm";
        UUID key = UUID.randomUUID();
        var first = call("POST", route, adminToken, key, command);
        assertEquals(200, first.status(), first.body().toString());
        var before = reservations(id);
        long audits = count("audit_log", id), events = count("outbox_event", id);
        var replay = call("POST", route, adminToken, key, command);
        assertEquals(200, replay.status()); assertEquals(first.body(), replay.body());
        var changed = call("POST", route, adminToken, key, new VersionCommand(command.expectedLockVersion() + 1));
        assertEquals(409, changed.status()); assertEquals("IDEMPOTENCY_KEY_REUSED", changed.body().path("error").path("code").asText());
        assertEquals(before, reservations(id)); assertEquals(2, before.size());
        assertEquals(audits, count("audit_log", id)); assertEquals(events, count("outbox_event", id));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM command_receipt WHERE actor_id=? AND idempotency_key=?", Integer.class, admin, key.toString()));
    }

    @Test void conflictPreviewReturnsJointAlternativeWithoutWritingBusinessState() throws Exception {
        UUID id = open(plan(venue, equipment, 0, 60, 30, 2), true);
        confirm(approved(owner2Token, plan(venue, equipment, 270, 120, 0, 1), false));
        var before = reservations(id);
        long audits = count("audit_log", id), events = count("outbox_event", id);
        var target = plan(venue, equipment, 300, 60, 30, 2);
        var response = call("POST", "/activities/" + id + "/change-preview", ownerToken, null, new Proposal(1, target));
        assertEquals(200, response.status(), response.body().toString());
        var preview = mapper.treeToValue(response.data(), ChangePreview.class);
        assertFalse(preview.check().feasible());
        assertTrue(preview.check().conflicts().stream().anyMatch(c -> c.code() == ConflictCode.VENUE_OCCUPIED));
        assertTrue(preview.check().conflicts().stream().anyMatch(c -> c.code() == ConflictCode.EQUIPMENT_SHORTAGE
                && Objects.equals(c.required(), 3) && Objects.equals(c.available(), 2)));
        var first = preview.alternatives().items().getFirst();
        assertEquals(plan(venue2, equipment2, 300, 60, 30, 2), first.plan());
        assertEquals(0, first.timeShiftMinutes()); assertTrue(first.venueChanged());
        assertEquals(1, first.replacedEquipmentLines());
        assertEquals(0, count("change_request", id));
        assertEquals(audits, count("audit_log", id)); assertEquals(events, count("outbox_event", id));
        originalUnchanged(id, before);
    }

    @Test void realChangeSwitchesReservationsRegistrationAndDeliversVersionedNotifications() throws Exception {
        UUID id = open(plan(venue, equipment, 0, 60, 30, 2), true);
        var target = plan(venue2, equipment, 30, 60, 30, 2); // 与自身旧设备占用重叠，必须排除自身。
        UUID change = readyChange(id, target, true);
        var frozen = call("PUT", "/activities/" + id + "/registration", studentToken, UUID.randomUUID(),
                new RegistrationCommand(RegistrationAction.WITHDRAW, 1));
        assertEquals(409, frozen.status()); assertEquals("CHANGE_PENDING", frozen.body().path("error").path("code").asText());
        var result = write("POST", "/changes/" + change + "/resource-decision", adminToken,
                new ChangeDecision(Decision.APPROVE, "资源齐备"), 200);
        assertEquals("APPLIED", result.path("status").asText()); assertEquals(2, result.path("appliedVersionNo").asInt());
        var current = get("/activities/" + id, ownerToken);
        assertEquals(2, current.path("versionNo").asInt()); assertEquals(2, current.path("effectiveVersionNo").asInt());
        assertEquals(target, mapper.treeToValue(current.path("input").path("plan"), Plan.class));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM reservation WHERE activity_id=? AND version_no=1 AND status='RELEASED'", Integer.class, id));
        assertEquals(2, jdbc.queryForObject("SELECT count(*) FROM reservation WHERE activity_id=? AND version_no=2 AND status='ACTIVE'", Integer.class, id));
        assertEquals(2, jdbc.queryForObject("SELECT version_no FROM activity_registration WHERE activity_id=? AND user_id=? AND status='ACTIVE'", Integer.class, id, student));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE activity_id=? AND event_type='CHANGE_APPLIED' AND version_no=2", Integer.class, id));
        UUID event = jdbc.queryForObject("SELECT id FROM outbox_event WHERE activity_id=? AND event_type='CHANGE_APPLIED' AND version_no=2", UUID.class, id);
        consumer.drain(); consumer.drain();
        assertEquals("DONE", jdbc.queryForObject("SELECT status FROM outbox_event WHERE id=?", String.class, event));
        assertEquals(Set.of(owner, staff, student), new HashSet<>(jdbc.queryForList("SELECT recipient_id FROM notification WHERE event_id=? AND version_no=2", UUID.class, event)));
        assertEquals(3, jdbc.queryForObject("SELECT count(*) FROM notification WHERE event_id=?", Integer.class, event));
        var messages = get("/notifications", studentToken);
        var delivered = java.util.stream.StreamSupport.stream(messages.spliterator(), false)
                .filter(n -> event.toString().equals(n.path("eventId").asText())).findFirst().orElseThrow();
        write("POST", "/notifications/" + delivered.path("id").asText() + "/read", studentToken, null, 200);
        assertEquals("APPLIED", get("/changes/" + change, ownerToken).path("status").asText());
    }

    @Test void lateResourceCompetitionCommitsConflictAndKeepsRiskyOriginalArrangement() throws Exception {
        UUID id = open(plan(venue, equipment, 0, 60, 0, 2), false);
        var target = plan(venue2, equipment2, 180, 60, 0, 2);
        var preview = call("POST", "/activities/" + id + "/schedule-preview", ownerToken, null, new Proposal(1, target));
        assertEquals(200, preview.status()); assertTrue(preview.data().path("feasible").asBoolean());
        UUID change = readyChange(id, target, false);
        confirm(approved(owner2Token, target, false));
        write("POST", "/resources/" + equipment + "/faults", adminToken,
                new FaultInput(START, START.plusSeconds(3600), 2, "原设备故障"), 201);
        var before = reservations(id);
        var response = call("POST", "/changes/" + change + "/resource-decision", adminToken, UUID.randomUUID(),
                new ChangeDecision(Decision.APPROVE, "最终重查"));
        assertEquals(409, response.status(), response.body().toString());
        assertEquals("RESOURCE_CONFLICT", response.body().path("error").path("code").asText());
        assertEquals("CONFLICTED", response.body().path("error").path("details").path("change").path("status").asText());
        assertEquals("CONFLICTED", get("/changes/" + change, ownerToken).path("status").asText());
        assertEquals("AT_RISK", get("/activities/" + id, ownerToken).path("resourceRisk").asText());
        originalUnchanged(id, before);
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE activity_id=? AND event_type='CHANGE_APPLIED'", Integer.class, id));
    }

    @Test void finalConfirmationUsesLatestEligibilityAndLeavesOriginalReservations() throws Exception {
        UUID id = open(plan(venue, equipment, 0, 60, 0, 1), false);
        var target = plan(venue2, equipment2, 180, 60, 0, 2);
        UUID change = readyChange(id, target, false);
        var before = reservations(id);
        write("PATCH", "/resources/" + equipment2 + "/eligibility", adminToken, new ResourceEligibility(false, true), 200);
        UUID key = UUID.randomUUID();
        var response = call("POST", "/changes/" + change + "/resource-decision", adminToken, key,
                new ChangeDecision(Decision.APPROVE, "最终资格重查"));
        assertEquals(403, response.status());
        assertEquals("RESOURCE_INELIGIBLE", response.body().path("error").path("code").asText());
        assertEquals("PENDING_RESOURCE", get("/changes/" + change, ownerToken).path("status").asText());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM command_receipt WHERE actor_id=? AND idempotency_key=?", Integer.class, admin, key.toString()));
        originalUnchanged(id, before);
    }

    @Test void reservationInsertFailureRollsBackWholeChangeAndSameKeyCanRetry() throws Exception {
        UUID id = open(plan(venue, equipment, 0, 60, 0, 1), true);
        var target = plan(venue2, equipment2, 180, 60, 0, 2);
        UUID change = readyChange(id, target, true);
        var before = reservations(id);
        var activityBefore = get("/activities/" + id, ownerToken);
        long audits = count("audit_log", id), events = count("outbox_event", id);
        jdbc.execute("""
                CREATE FUNCTION ds_workflow_fail() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.version_no=2 AND NEW.quantity=2 THEN RAISE EXCEPTION 'DS改期预约写入失败'; END IF; RETURN NEW; END $$
                """);
        jdbc.execute("CREATE TRIGGER ds_workflow_fail BEFORE INSERT ON reservation FOR EACH ROW EXECUTE FUNCTION ds_workflow_fail()");
        UUID key = UUID.randomUUID();
        var input = new ChangeDecision(Decision.APPROVE, "预约异常回滚检查");
        try {
            var response = call("POST", "/changes/" + change + "/resource-decision", adminToken, key, input);
            assertEquals(500, response.status(), response.body().toString());
            assertEquals("INTERNAL_ERROR", response.body().path("error").path("code").asText());
            originalUnchanged(id, before);
            assertEquals(activityBefore, get("/activities/" + id, ownerToken));
            assertEquals("PENDING_RESOURCE", get("/changes/" + change, ownerToken).path("status").asText());
            assertEquals(audits, count("audit_log", id)); assertEquals(events, count("outbox_event", id));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM command_receipt WHERE actor_id=? AND idempotency_key=?", Integer.class, admin, key.toString()));
        } finally {
            jdbc.execute("DROP TRIGGER ds_workflow_fail ON reservation");
            jdbc.execute("DROP FUNCTION ds_workflow_fail()");
        }
        var retry = call("POST", "/changes/" + change + "/resource-decision", adminToken, key, input);
        assertEquals(200, retry.status(), retry.body().toString()); assertEquals("APPLIED", retry.data().path("status").asText());
        assertEquals(2, count("activity_version", id));
    }

    @Test void twoRealConfirmRequestsProduceOneWinnerWithoutPartialReservations() throws Exception {
        var target = plan(venue, equipment, 0, 60, 0, 2);
        UUID a = approved(ownerToken, target, false), b = approved(owner2Token, target, false);
        int version = get("/activities/" + a, ownerToken).path("lockVersion").asInt();
        var barrier = new CyclicBarrier(2);
        var pool = Executors.newFixedThreadPool(2);
        try {
            var requests = new ArrayList<Future<Response>>();
            for (UUID id : List.of(a, b)) requests.add(pool.submit(() -> {
                barrier.await(10, TimeUnit.SECONDS);
                return call("POST", "/activities/" + id + "/resource-confirm", adminToken, UUID.randomUUID(), new VersionCommand(version));
            }));
            var responses = List.of(requests.get(0).get(20, TimeUnit.SECONDS), requests.get(1).get(20, TimeUnit.SECONDS));
            assertEquals(List.of(200, 409), responses.stream().map(Response::status).sorted().toList(), responses.toString());
            int winner = responses.get(0).status() == 200 ? 0 : 1;
            UUID won = List.of(a, b).get(winner), lost = List.of(a, b).get(1 - winner);
            assertEquals("RESOURCE_CONFLICT", responses.get(1 - winner).body().path("error").path("code").asText());
            assertEquals(2, count("reservation", won)); assertEquals(0, count("reservation", lost));
            assertEquals("SCHEDULED", get("/activities/" + won, adminToken).path("status").asText());
            assertEquals("APPROVED_UNRESERVED", get("/activities/" + lost, adminToken).path("status").asText());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reservation WHERE resource_id=? AND quantity>2", Integer.class, equipment));
        } finally {
            pool.shutdownNow(); assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
    }
}
