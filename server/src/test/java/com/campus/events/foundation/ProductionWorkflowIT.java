package com.campus.events.foundation;

import com.campus.events.CampusEventsApplication;
import com.campus.events.contract.*;
import com.campus.events.notification.NotificationConsumer;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.junit.jupiter.api.Assertions.*;

/** PM-04：五类真实生产端口、HTTP与PostgreSQL；无CM/通知替身，不替代QA全量验收。 */
@SpringBootTest(classes = CampusEventsApplication.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@Import(ProductionWorkflowIT.FixedTime.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ProductionWorkflowIT {
    @TestConfiguration static class FixedTime {
        @Bean @Primary Clock workflowClock() { return Clock.fixed(Instant.parse("2030-10-19T00:00:00Z"), ZoneOffset.UTC); }
    }
    static final String SCHEMA = "t_" + UUID.randomUUID().toString().replace("-", "");
    static String url, username, password;
    static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("PM真库联调缺少" + key);
        return value;
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        url = required("TEST_DATABASE_URL"); username = required("TEST_DB_USERNAME"); password = required("TEST_DB_PASSWORD");
        if (!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/campus_test"))
            throw new IllegalStateException("只允许本机campus_test随机schema");
        properties.add("spring.datasource.url", () -> url + "?currentSchema=" + SCHEMA);
        properties.add("spring.datasource.username", () -> username);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("app.demo-seed", () -> "false");
    }
    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountPort accounts;
    @Autowired ChangePort changes;
    @Autowired NotificationConsumer consumer;
    final UUID org = UUID.randomUUID(), admin = UUID.randomUUID();
    UUID owner, staff, student;
    String adminToken, ownerToken, staffToken, studentToken, approverToken;
    final List<Permission> adminPermissions = List.of(Permission.USER_MANAGE, Permission.RESOURCE_MANAGE,
            Permission.RESOURCE_CONFIRM, Permission.AUDIT_READ);
    @BeforeAll void seed() throws Exception {
        jdbc.update("INSERT INTO organization(id,name) VALUES (?,?)", org, "PM真实业务联调组织");
        jdbc.update("INSERT INTO app_user(id,organization_id,username,display_name,password_hash,identity) VALUES (?,?,?,?,?,'TEACHER')",
                admin, org, admin.toString(), "联调管理员", encoder.encode(required("DEMO_PASSWORD")));
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,'ADMIN')", admin);
        for (Permission permission : adminPermissions)
            jdbc.update("INSERT INTO user_permission(user_id,permission) VALUES (?,?)", admin, permission.name());
        adminToken = login(admin.toString());
        resource(); // 审批范围验证必须已有真实资源类别。
        owner = user(Identity.TEACHER, List.of(Role.APPLICANT), List.of());
        staff = user(Identity.TEACHER, List.of(Role.APPLICANT), List.of());
        student = user(Identity.STUDENT, List.of(Role.APPLICANT), List.of());
        UUID approver = user(Identity.TEACHER, List.of(Role.APPROVER), List.of(new ApprovalScope(org, List.of("VENUE"))));
        ownerToken = login(owner); staffToken = login(staff);
        studentToken = login(student); approverToken = login(approver);
    }
    @AfterAll void cleanup() {
        if (url != null) new JdbcTemplate(new DriverManagerDataSource(url, username, password)).execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }
    record Result(int status, JsonNode body) { JsonNode data() { return body.path("data"); } }
    Result call(String method, String path, String token, UUID key, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), "/api/v1" + path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        if (body != null) request.contentType("application/json").content(mapper.writeValueAsBytes(body));
        var response = http.perform(request).andReturn().getResponse();
        var result = new Result(response.getStatus(), mapper.readTree(response.getContentAsString(StandardCharsets.UTF_8)));
        assertTrue(result.body().hasNonNull("requestId"));
        assertTrue(result.body().has("data") ^ result.body().has("error"));
        return result;
    }
    JsonNode ok(int expected, String method, String path, String token, Object body) throws Exception {
        var result = call(method, path, token, method.equals("GET") ? null : UUID.randomUUID(), body);
        assertEquals(expected, result.status(), result.body().toString()); return result.data();
    }
    String login(String name) throws Exception {
        return ok(200, "POST", "/auth/login", null, new LoginInput(name, required("DEMO_PASSWORD"))).path("token").asText();
    }
    String login(UUID id) throws Exception { return login(jdbc.queryForObject("SELECT username FROM app_user WHERE id=?", String.class, id)); }
    UUID user(Identity identity, List<Role> roles, List<ApprovalScope> scopes) throws Exception {
        String name = UUID.randomUUID().toString();
        var data = ok(201, "POST", "/users", adminToken, new UserCreate(identity, roles, List.of(), scopes, List.of(), name,
                "联调账号", required("DEMO_PASSWORD"), org));
        return UUID.fromString(data.path("id").asText());
    }
    UUID resource() throws Exception {
        return UUID.fromString(ok(201, "POST", "/resources", adminToken,
                new ResourceCreate(true, true, ResourceKind.VENUE, "联调场地" + UUID.randomUUID(), "VENUE", 80, 1, "08:00", "22:00")).path("id").asText());
    }
    Plan plan(UUID venue, String start, String end) { return new Plan(Instant.parse(start), Instant.parse(end), 0, 0, venue, List.of()); }
    record Flow(UUID id, Plan original, Plan target) {}
    Flow published(boolean withStaff) throws Exception {
        Plan original = plan(resource(), "2030-10-20T02:00:00Z", "2030-10-20T03:00:00Z");
        Plan target = plan(resource(), "2030-10-20T07:00:00Z", "2030-10-20T08:00:00Z");
        var created = ok(201, "POST", "/activities", ownerToken,
                new ActivityInput("PM全生产端口联调", "公开简介", 10, 1, withStaff ? List.of(staff) : List.of(), original));
        UUID id = UUID.fromString(created.path("id").asText());
        var submitted = ok(200, "POST", "/activities/" + id + "/submit", ownerToken, new VersionCommand(created.path("lockVersion").asInt()));
        // 有意见的合法首次审批路径；合法空意见缺陷仍由原QA用例暴露，不改其断言。
        var approved = ok(200, "POST", "/activities/" + id + "/approver-decision", approverToken,
                new ApprovalCommand(submitted.path("lockVersion").asInt(), Decision.APPROVE, "联调审批同意"));
        var scheduled = ok(200, "POST", "/activities/" + id + "/resource-confirm", adminToken, new VersionCommand(approved.path("lockVersion").asInt()));
        var open = ok(200, "POST", "/activities/" + id + "/publish", ownerToken, new VersionCommand(scheduled.path("lockVersion").asInt()));
        assertEquals("OPEN", open.path("status").asText());
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM reservation WHERE activity_id=? AND status='ACTIVE'", Integer.class, id));
        return new Flow(id, original, target);
    }
    JsonNode activity(UUID id) throws Exception { return ok(200, "GET", "/activities/" + id, ownerToken, null); }
    UUID change(Flow flow) throws Exception {
        var preview = call("POST", "/activities/" + flow.id() + "/change-preview", ownerToken, null, new Proposal(1, flow.target()));
        assertEquals(200, preview.status()); assertTrue(preview.data().path("check").path("feasible").asBoolean());
        var created = ok(201, "POST", "/activities/" + flow.id() + "/changes", ownerToken,
                new ChangeCreate(1, flow.target(), activity(flow.id()).path("lockVersion").asInt(), "联调调整场地与时间"));
        return UUID.fromString(created.path("id").asText());
    }
    void approveChange(UUID id) throws Exception {
        assertEquals("PENDING_RESOURCE", ok(200, "POST", "/changes/" + id + "/approver-decision", approverToken,
                new ChangeDecision(Decision.APPROVE, "")).path("status").asText());
    }
    @Test void allProductionPortsPersistChangeNotificationsAndCancellation() throws Exception {
        Flow flow = published(true);
        ok(200, "PUT", "/activities/" + flow.id() + "/registration", studentToken, new RegistrationCommand(RegistrationAction.JOIN, 1));
        UUID change = change(flow); assertEquals(change, changes.pendingId(flow.id()));
        var frozen = call("PUT", "/activities/" + flow.id() + "/registration", studentToken, UUID.randomUUID(), new RegistrationCommand(RegistrationAction.WITHDRAW, 1));
        assertEquals(409, frozen.status()); assertEquals("CHANGE_PENDING", frozen.body().path("error").path("code").asText());
        var confirmed = ok(200, "POST", "/changes/" + change + "/confirmation", staffToken, new ChangeDecision(Decision.APPROVE, ""));
        assertEquals("PENDING_APPROVER", confirmed.path("status").asText()); approveChange(change);
        var applied = ok(200, "POST", "/changes/" + change + "/resource-decision", adminToken, new ChangeDecision(Decision.APPROVE, ""));
        assertEquals("APPLIED", applied.path("status").asText()); assertEquals(2, applied.path("appliedVersionNo").asInt());
        assertNull(changes.pendingId(flow.id())); assertEquals(2, activity(flow.id()).path("effectiveVersionNo").asInt());
        assertEquals(flow.target().venueId().toString(), activity(flow.id()).path("input").path("plan").path("venueId").asText());
        assertEquals(2, jdbc.queryForObject("SELECT version_no FROM activity_registration WHERE activity_id=? AND user_id=?", Integer.class, flow.id(), student));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM reservation WHERE activity_id=? AND status='ACTIVE' AND resource_id=? AND version_no=2", Integer.class, flow.id(), flow.target().venueId()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reservation WHERE activity_id=? AND status='ACTIVE' AND resource_id=?", Integer.class, flow.id(), flow.original().venueId()));
        ok(200, "GET", "/activities/" + flow.id() + "/audit", adminToken, null);
        ok(200, "POST", "/activities/" + flow.id() + "/cancel", ownerToken, new ReasonCommand(activity(flow.id()).path("lockVersion").asInt(), "联调取消"));
        assertEquals("CANCELLED", activity(flow.id()).path("status").asText());
        assertEquals("CANCELLED", jdbc.queryForObject("SELECT status FROM activity_registration WHERE activity_id=? AND user_id=?", String.class, flow.id(), student));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM reservation WHERE activity_id=? AND status='ACTIVE'", Integer.class, flow.id()));
        for (int i = 0; i < 100; i++) {
            consumer.drain();
            if (jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE activity_id=? AND status<>'DONE'", Integer.class, flow.id()) == 0) break;
            Thread.sleep(50);
        }
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM outbox_event WHERE activity_id=? AND status<>'DONE'", Integer.class, flow.id()));
        assertEquals(jdbc.queryForObject("SELECT coalesce(sum(jsonb_array_length(payload_json->'recipientIds')),0) FROM outbox_event WHERE activity_id=?", Long.class, flow.id()),
                jdbc.queryForObject("SELECT count(*) FROM notification WHERE activity_id=?", Long.class, flow.id()));
        assertFalse(ok(200, "GET", "/notifications", studentToken, null).isEmpty());
    }
    @Test void currentResourceScopeBlocksFinalChangeAndTheSameKeyCanRetryAfterRestoration() throws Exception {
        Flow flow = published(false); UUID change = change(flow); approveChange(change);
        var scope = accounts.get(admin).managedResourceIds(); UUID key = UUID.randomUUID();
        var command = new ChangeDecision(Decision.APPROVE, "资源复核同意");
        int auditBefore = jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE activity_id=?", Integer.class, flow.id());
        try {
            ok(200, "PATCH", "/users/" + admin + "/authorization", adminToken,
                    new AuthorizationInput(Identity.TEACHER, List.of(Role.ADMIN), adminPermissions, List.of(), List.of(flow.original().venueId())));
            var denied = call("POST", "/changes/" + change + "/resource-decision", adminToken, key, command);
            assertEquals(403, denied.status());
            assertEquals("PENDING_RESOURCE", jdbc.queryForObject("SELECT status FROM change_request WHERE id=?", String.class, change));
            assertEquals(1, activity(flow.id()).path("effectiveVersionNo").asInt());
            assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM reservation WHERE activity_id=? AND status='ACTIVE' AND resource_id=?", Integer.class, flow.id(), flow.original().venueId()));
            assertEquals(auditBefore, jdbc.queryForObject("SELECT count(*) FROM audit_log WHERE activity_id=?", Integer.class, flow.id()));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM command_receipt WHERE actor_id=? AND idempotency_key=?", Integer.class, admin, key.toString()));
        } finally {
            ok(200, "PATCH", "/users/" + admin + "/authorization", adminToken,
                    new AuthorizationInput(Identity.TEACHER, List.of(Role.ADMIN), adminPermissions, List.of(), scope));
        }
        var applied = call("POST", "/changes/" + change + "/resource-decision", adminToken, key, command);
        assertEquals(200, applied.status()); assertEquals("APPLIED", applied.data().path("status").asText());
        var replay = call("POST", "/changes/" + change + "/resource-decision", adminToken, key, command);
        assertEquals(applied.body(), replay.body());
    }
}
