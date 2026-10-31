package com.campus.events.foundation;

import com.campus.events.contract.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.boot.autoconfigure.security.servlet.UserDetailsServiceAutoConfiguration;
import org.springframework.boot.test.autoconfigure.web.servlet.*;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.*;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.*;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import static org.junit.jupiter.api.Assertions.*;

/** PM-04：真实PM/AN/DS模块及数据库。显式不装配CM，验证缺端口时拒绝并整体回滚；无端口替身。 */
@SpringBootTest(classes = PublishedModulesIT.PublishedApplication.class)
@AutoConfigureMockMvc(print = MockMvcPrint.NONE)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PublishedModulesIT {
    @Configuration
    @org.springframework.boot.test.context.TestComponent
    @EnableAutoConfiguration(exclude = UserDetailsServiceAutoConfiguration.class)
    @ComponentScan(basePackages = {"com.campus.events.foundation", "com.campus.events.auth",
            "com.campus.events.activity", "com.campus.events.registration", "com.campus.events.resource",
            "com.campus.events.scheduling"}, excludeFilters = @ComponentScan.Filter(
                    type = FilterType.ANNOTATION, classes = org.springframework.boot.test.context.TestComponent.class))
    static class PublishedApplication {
        @Bean @Primary Clock integrationClock() { return Clock.fixed(Instant.parse("2030-10-19T00:00:00Z"), ZoneOffset.UTC); }
    }
    static final String SCHEMA = "t_" + UUID.randomUUID().toString().replace("-", "");
    static String url, username, password;
    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired PasswordEncoder encoder;
    @Autowired AccountPort accounts;
    @Autowired ActivityPort activities;
    @Autowired SchedulingPort scheduling;
    final UUID org = UUID.randomUUID(), owner = UUID.randomUUID(), admin = UUID.randomUUID();
    String ownerToken, adminToken;
    UUID venue;

    static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("PM集成检查缺少" + key);
        return value;
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        url = required("TEST_DATABASE_URL"); username = required("TEST_DB_USERNAME"); password = required("TEST_DB_PASSWORD");
        if (!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/campus_test"))
            throw new IllegalStateException("只允许本机campus_test独立schema");
        new JdbcTemplate(new DriverManagerDataSource(url, username, password)).execute("CREATE SCHEMA " + SCHEMA);
        properties.add("spring.datasource.url", () -> url + "?currentSchema=" + SCHEMA);
        properties.add("spring.datasource.username", () -> username);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("app.demo-seed", () -> "false");
    }
    @BeforeAll void seed() throws Exception {
        jdbc.update("INSERT INTO organization(id,name) VALUES (?,?)", org, "PM模块接收测试");
        for (UUID id : List.of(owner, admin)) jdbc.update("""
                INSERT INTO app_user(id,organization_id,username,display_name,password_hash,identity)
                VALUES (?,?,?,?,?,'TEACHER')
                """, id, org, id.toString(), "集成测试账号", encoder.encode(required("DEMO_PASSWORD")));
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,'APPLICANT'),(?,'ADMIN')", owner, admin);
        jdbc.update("INSERT INTO user_permission(user_id,permission) VALUES (?,'RESOURCE_MANAGE')", admin);
        ownerToken = login(owner); adminToken = login(admin);
        venue = resource("集成测试场地");
    }
    @AfterAll void cleanup() {
        if (url != null && SCHEMA.matches("t_[0-9a-f]{32}"))
            new JdbcTemplate(new DriverManagerDataSource(url, username, password)).execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }
    record Result(int status, JsonNode body) { JsonNode data() { return body.path("data"); } }
    Result call(String method, String path, String token, UUID key, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), "/api/v1" + path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key);
        if (body != null) request.contentType("application/json").content(mapper.writeValueAsBytes(body));
        var response = http.perform(request).andReturn().getResponse();
        return new Result(response.getStatus(), mapper.readTree(response.getContentAsString(StandardCharsets.UTF_8)));
    }
    String login(UUID id) throws Exception {
        var result = call("POST", "/auth/login", null, null, new LoginInput(id.toString(), required("DEMO_PASSWORD")));
        assertEquals(200, result.status()); return result.data().path("token").asText();
    }
    UUID resource(String name) throws Exception {
        var result = call("POST", "/resources", adminToken, UUID.randomUUID(),
                new ResourceCreate(true, true, ResourceKind.VENUE, name, "VENUE", 80, 1, "08:00", "22:00"));
        assertEquals(201, result.status(), result.body().toString()); return UUID.fromString(result.data().path("id").asText());
    }
    Plan plan() { return new Plan(Instant.parse("2030-10-20T02:00:00Z"), Instant.parse("2030-10-20T03:00:00Z"), 0, 0, venue, List.of()); }
    UUID draft() throws Exception {
        var result = call("POST", "/activities", ownerToken, UUID.randomUUID(),
                new ActivityInput("真实模块集成草稿", "", 60, 20, List.of(), plan()));
        assertEquals(201, result.status(), result.body().toString()); return UUID.fromString(result.data().path("id").asText());
    }
    long count(String table, UUID id) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE activity_id=?", Long.class, id);
    }
    @Test void newResourceIsGrantedViaRealAccountPort() throws Exception {
        UUID id = resource("自动授权资源");
        assertTrue(accounts.get(admin).managedResourceIds().contains(id));
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM user_resource_scope WHERE user_id=? AND resource_id=?", Integer.class, admin, id));
    }
    @Test void persistedActivityPortAndSchedulingPreviewWorkWithoutMocks() throws Exception {
        UUID id = draft();
        var snapshot = activities.get(id);
        assertEquals(id, snapshot.activity().id()); assertNull(snapshot.activity().myRegistrationStatus());
        var result = call("POST", "/activities/" + id + "/schedule-preview", ownerToken, null, new Proposal(1, plan()));
        assertEquals(200, result.status(), result.body().toString()); assertTrue(result.data().path("feasible").asBoolean());
        assertTrue(scheduling.check(plan(), 60, id, Identity.TEACHER).feasible());
        assertEquals(0, count("reservation", id));
    }
    @Test void missingEventPortReturns503AndRollsBackReceiptAndActivity() throws Exception {
        UUID id = draft(), key = UUID.randomUUID();
        var result = call("POST", "/activities/" + id + "/submit", ownerToken, key, new VersionCommand(0));
        assertEquals(503, result.status(), result.body().toString());
        assertEquals("INTERNAL_ERROR", result.body().path("error").path("code").asText());
        assertTrue(result.body().hasNonNull("requestId")); assertFalse(result.body().has("data"));
        assertEquals("DRAFT", jdbc.queryForObject("SELECT status FROM activity WHERE id=?", String.class, id));
        assertEquals(0, jdbc.queryForObject("SELECT lock_version FROM activity WHERE id=?", Integer.class, id));
        assertEquals(0, count("reservation", id)); assertEquals(0, count("outbox_event", id));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM command_receipt WHERE actor_id=? AND idempotency_key=?", Integer.class, owner, key.toString()));
    }
    @Test void missingEventPortDoesNotLeaveFaultOrReceipt() throws Exception {
        UUID key = UUID.randomUUID();
        var result = call("POST", "/resources/" + venue + "/faults", adminToken, key,
                new FaultInput(plan().startAt(), plan().endAt(), 1, "验证缺依赖回滚"));
        assertEquals(503, result.status(), result.body().toString());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM resource_fault WHERE resource_id=?", Integer.class, venue));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM command_receipt WHERE actor_id=? AND idempotency_key=?", Integer.class, admin, key.toString()));
    }
}
