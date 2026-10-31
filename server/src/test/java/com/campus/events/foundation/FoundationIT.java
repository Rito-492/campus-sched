package com.campus.events.foundation;

import com.campus.events.CampusEventsApplication;
import com.campus.events.auth.*;
import com.campus.events.contract.*;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.nio.charset.StandardCharsets;
import java.sql.Timestamp;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.http.HttpMethod;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/** PM真实PostgreSQL检查；不依赖其他业务端口，不使用数据库替身。 */
@SpringBootTest(classes = CampusEventsApplication.class)
@AutoConfigureMockMvc(print = org.springframework.boot.test.autoconfigure.web.servlet.MockMvcPrint.NONE)
@Import(FoundationIT.FixedClock.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class FoundationIT {
    private static final Instant NOW = Instant.parse("2030-10-19T00:00:00Z");
    private static final String SCHEMA = "t_" + UUID.randomUUID().toString().replace("-", "");
    private static final String PASSWORD = required("DEMO_PASSWORD");
    private static String databaseUrl, databaseUser, databasePassword;
    @Autowired MockMvc http;
    @Autowired JdbcTemplate jdbc;
    @Autowired ObjectMapper mapper;
    @Autowired AccountPort accountPort;
    @Autowired CommandExecutor commands;
    @Autowired TransactionTemplate transactions;
    @Autowired PasswordEncoder encoder;
    @Autowired UserService users;
    @Autowired SessionService sessions;
    private final UUID org = UUID.randomUUID(), otherOrg = UUID.randomUUID(), venue = UUID.randomUUID(), projector = UUID.randomUUID();
    private Actor admin;
    private String adminToken;

    @TestConfiguration static class FixedClock {
        @Bean @Primary Clock integrationClock() { return Clock.fixed(NOW, ZoneOffset.UTC); }
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        databaseUrl = required("TEST_DATABASE_URL"); databaseUser = required("TEST_DB_USERNAME"); databasePassword = required("TEST_DB_PASSWORD");
        if (!databaseUrl.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/campus_test")) {
            throw new IllegalStateException("PM测试只允许明确的本地campus_test数据库，不接受额外URL参数");
        }
        new JdbcTemplate(new DriverManagerDataSource(databaseUrl, databaseUser, databasePassword)).execute("CREATE SCHEMA " + SCHEMA);
        properties.add("spring.datasource.url", () -> databaseUrl + "?currentSchema=" + SCHEMA);
        properties.add("spring.datasource.username", () -> databaseUser);
        properties.add("spring.datasource.password", () -> databasePassword);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("app.demo-seed", () -> "false");
    }
    private static String required(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) throw new IllegalStateException("必须配置" + name + "，缺少真实库不能通过");
        return value;
    }
    @BeforeAll void seed() throws Exception {
        jdbc.update("INSERT INTO organization(id,name) VALUES (?,?),(?,?)", org, "PM独立测试组织", otherOrg, "PM范围外组织");
        jdbc.update("""
                INSERT INTO resource(id,kind,name,category,capacity,quantity,open_from,open_to)
                VALUES (?,'VENUE','PM测试场地','VENUE',80,1,'08:00','22:00'),
                       (?,'EQUIPMENT','PM测试投影仪','PROJECTOR',NULL,2,'08:00','22:00')
                """, venue, projector);
        admin = createFixture(Identity.TEACHER, Role.ADMIN);
        jdbc.update("INSERT INTO user_permission(user_id,permission) VALUES (?,'USER_MANAGE')", admin.id());
        admin = accountPort.get(admin.id()); adminToken = login(admin.id().toString());
        jdbc.execute("CREATE TABLE pm_counter(id uuid PRIMARY KEY, value integer NOT NULL)");
    }
    @AfterAll void cleanup() {
        if (databaseUrl != null && SCHEMA.matches("t_[0-9a-f]{32}")) new JdbcTemplate(new DriverManagerDataSource(databaseUrl, databaseUser, databasePassword))
                .execute("DROP SCHEMA IF EXISTS " + SCHEMA + " CASCADE");
    }
    private Actor createFixture(Identity identity, Role role) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO app_user(id,organization_id,username,display_name,password_hash,identity) VALUES (?,?,?,?,?,?)",
                id, org, id.toString(), "PM测试账号", encoder.encode(PASSWORD), identity.name());
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,?)", id, role.name());
        return accountPort.get(id);
    }
    private record Result(int status, JsonNode body) { JsonNode data() { return body.path("data"); } }
    private Result call(String method, String path, String token, UUID key, Object body) throws Exception {
        var request = MockMvcRequestBuilders.request(HttpMethod.valueOf(method), "/api/v1" + path);
        if (token != null) request.header("Authorization", "Bearer " + token);
        if (key != null) request.header("Idempotency-Key", key.toString());
        if (body != null) request.contentType("application/json").content(mapper.writeValueAsBytes(body));
        var response = http.perform(request).andReturn().getResponse();
        return new Result(response.getStatus(), mapper.readTree(response.getContentAsString(StandardCharsets.UTF_8)));
    }
    private String login(String username) throws Exception {
        Result result = call("POST", "/auth/login", null, null, Map.of("username", username, "password", PASSWORD));
        assertEquals(200, result.status(), result.body().toString());
        return result.data().path("token").asText();
    }
    private AuthorizationInput authorization(Identity identity, List<Role> roles, List<Permission> permissions,
                                             List<ApprovalScope> scopes, List<UUID> resources) {
        return new AuthorizationInput(identity, roles, permissions, scopes, resources);
    }
    private long count(String table, UUID id) { return jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE user_id=?", Long.class, id); }

    @Test void loginIsHashedRedactedAndExpiresAfterEightHours() throws Exception {
        Actor actor = createFixture(Identity.TEACHER, Role.APPLICANT);
        Result result = call("POST", "/auth/login", null, null, new LoginInput(actor.id().toString(), PASSWORD));
        assertEquals(200, result.status());
        assertEquals(NOW.plus(Duration.ofHours(8)), Instant.parse(result.data().path("expiresAt").asText()));
        String token = result.data().path("token").asText();
        assertNotEquals(token, jdbc.queryForObject("SELECT token_hash FROM auth_session WHERE user_id=?", String.class, actor.id()));
        assertEquals(CanonicalJson.sha256(token), jdbc.queryForObject("SELECT token_hash FROM auth_session WHERE user_id=?", String.class, actor.id()));
        assertFalse(result.body().toString().contains("password"));
        assertEquals(200, call("GET", "/auth/me", token, null, null).status());
        jdbc.update("UPDATE auth_session SET expires_at=? WHERE user_id=?", Timestamp.from(NOW), actor.id());
        assertEquals(401, call("GET", "/auth/me", token, null, null).status());
    }
    @Test void invalidCredentialsAndUnknownUserAreUnauthenticated() throws Exception {
        assertEquals(401, call("POST", "/auth/login", null, null, new LoginInput(admin.id().toString(), "wrong")).status());
        assertEquals(401, call("POST", "/auth/login", null, null, new LoginInput("missing", PASSWORD)).status());
    }
    @Test void logoutInvalidatesCurrentSessionOnly() throws Exception {
        Actor actor = createFixture(Identity.STUDENT, Role.APPLICANT);
        String first = login(actor.id().toString()), second = login(actor.id().toString());
        assertEquals(200, call("POST", "/auth/logout", first, null, null).status());
        assertEquals(401, call("GET", "/auth/me", first, null, null).status());
        assertEquals(200, call("GET", "/auth/me", second, null, null).status());
    }
    @Test void disablingRevokesSessionsAndRecordsBeforeAndAfter() throws Exception {
        Actor target = createFixture(Identity.STUDENT, Role.APPLICANT);
        String token = login(target.id().toString());
        var result = call("PATCH", "/users/" + target.id() + "/enabled", adminToken, UUID.randomUUID(), new EnabledInput(false));
        assertEquals(200, result.status()); assertEquals(0, count("auth_session", target.id()));
        assertEquals(401, call("GET", "/auth/me", token, null, null).status());
        assertEquals(401, call("POST", "/auth/login", null, null, new LoginInput(target.id().toString(), PASSWORD)).status());
        assertEquals(1, count("authorization_change", target.id()));
        JsonNode before = mapper.readTree(jdbc.queryForObject("SELECT before_json::text FROM authorization_change WHERE user_id=?", String.class, target.id()));
        JsonNode after = mapper.readTree(jdbc.queryForObject("SELECT after_json::text FROM authorization_change WHERE user_id=?", String.class, target.id()));
        assertTrue(before.path("enabled").asBoolean()); assertFalse(after.path("enabled").asBoolean());
        assertEquals(200, call("PATCH", "/users/" + target.id() + "/enabled", adminToken, UUID.randomUUID(), new EnabledInput(true)).status());
        assertEquals(401, call("GET", "/auth/me", token, null, null).status());
    }
    @Test void createUserUsesPasswordHashAndReplaysSameResponse() throws Exception {
        String username = "created-" + UUID.randomUUID(); UUID key = UUID.randomUUID();
        var input = new UserCreate(Identity.STUDENT, List.of(Role.APPLICANT), List.of(), List.of(), List.of(), username, "新账号", PASSWORD, org);
        var first = call("POST", "/users", adminToken, key, input);
        var replay = call("POST", "/users", adminToken, key, input);
        assertEquals(201, first.status(), first.body().toString()); assertEquals(first.body(), replay.body());
        UUID id = UUID.fromString(first.data().path("id").asText());
        String hash = jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class, id);
        assertTrue(encoder.matches(PASSWORD, hash)); assertNotEquals(PASSWORD, hash);
        assertEquals(1, count("authorization_change", id));
        assertEquals(409, call("POST", "/users", adminToken, UUID.randomUUID(), input).status());
    }
    @Test void fullAuthorizationReplacementChangesLatestSessionActor() throws Exception {
        Actor target = createFixture(Identity.TEACHER, Role.APPLICANT); String token = login(target.id().toString());
        var grant = authorization(Identity.STUDENT, List.of(Role.ADMIN, Role.APPROVER), List.of(Permission.RESOURCE_CONFIRM),
                List.of(new ApprovalScope(org, List.of("VENUE", "PROJECTOR"))), List.of(venue, projector));
        assertEquals(200, call("PATCH", "/users/" + target.id() + "/authorization", adminToken, UUID.randomUUID(), grant).status());
        var current = call("GET", "/auth/me", token, null, null);
        assertEquals("STUDENT", current.data().path("identity").asText());
        assertEquals(2, current.data().path("roles").size());
        var revoke = authorization(Identity.STUDENT, List.of(Role.APPLICANT), List.of(), List.of(), List.of());
        assertEquals(200, call("PATCH", "/users/" + target.id() + "/authorization", adminToken, UUID.randomUUID(), revoke).status());
        assertEquals(0, count("user_permission", target.id())); assertEquals(0, count("user_resource_scope", target.id()));
        assertEquals(0, count("user_approval_scope", target.id())); assertEquals(2, count("authorization_change", target.id()));
    }
    @Test void newPasswordMinimumAndUtf8LimitAreEnforcedWithoutWrites() throws Exception {
        for (String rejected : List.of(UUID.randomUUID().toString().substring(0, 7), "汉".repeat(25))) {
            String username = "bad-password-" + UUID.randomUUID(); UUID key = UUID.randomUUID();
            var input = new UserCreate(Identity.STUDENT, List.of(Role.APPLICANT), List.of(), List.of(), List.of(),
                    username, "密码边界账号", rejected, org);
            assertEquals(400, call("POST", "/users", adminToken, key, input).status());
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM app_user WHERE username=?", Integer.class, username));
            assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM command_receipt WHERE actor_id=? AND idempotency_key=?", Integer.class, admin.id(), key.toString()));
        }
        String accepted = UUID.randomUUID().toString().substring(0, 8);
        var valid = new UserCreate(Identity.STUDENT, List.of(Role.APPLICANT), List.of(), List.of(), List.of(),
                "eight-char-" + UUID.randomUUID(), "八位密码账号", accepted, org);
        var response = call("POST", "/users", adminToken, UUID.randomUUID(), valid);
        assertEquals(201, response.status());
        assertTrue(encoder.matches(accepted, jdbc.queryForObject("SELECT password_hash FROM app_user WHERE id=?", String.class,
                UUID.fromString(response.data().path("id").asText()))));
    }
    @Test void inconsistentOrNonexistentAuthorizationFailsWithoutHistory() throws Exception {
        Actor target = createFixture(Identity.STUDENT, Role.APPLICANT);
        for (var invalid : List.of(
                authorization(Identity.STUDENT, List.of(Role.APPLICANT), List.of(Permission.USER_MANAGE), List.of(), List.of()),
                authorization(Identity.STUDENT, List.of(Role.APPROVER), List.of(), List.of(new ApprovalScope(org, List.of("*"))), List.of()),
                authorization(Identity.STUDENT, List.of(Role.APPROVER), List.of(), List.of(new ApprovalScope(UUID.randomUUID(), List.of("VENUE"))), List.of()),
                authorization(Identity.STUDENT, List.of(Role.ADMIN), List.of(), List.of(), List.of(UUID.randomUUID())),
                authorization(Identity.STUDENT, List.of(Role.APPLICANT, Role.APPLICANT), List.of(), List.of(), List.of()))) {
            assertEquals(400, call("PATCH", "/users/" + target.id() + "/authorization", adminToken, UUID.randomUUID(), invalid).status());
        }
        assertEquals(0, count("authorization_change", target.id()));
        assertEquals(List.of(Role.APPLICANT), accountPort.get(target.id()).roles());
    }
    @Test void privilegedQueriesAreDeniedAndStaffDirectoryIsRedacted() throws Exception {
        Actor applicant = createFixture(Identity.STUDENT, Role.APPLICANT); String token = login(applicant.id().toString());
        assertEquals(403, call("GET", "/users", token, null, null).status());
        assertEquals(403, call("GET", "/organizations", token, null, null).status());
        var staff = call("GET", "/users?purpose=staff", token, null, null);
        assertEquals(200, staff.status()); assertTrue(staff.body().has("truncated"));
        for (JsonNode user : staff.data()) { assertEquals(org.toString(), user.path("organizationId").asText()); assertFalse(user.has("roles")); assertFalse(user.has("identity")); }
        assertEquals(200, call("GET", "/organizations", adminToken, null, null).status());
    }
    @Test void listsReportActualTruncation() throws Exception {
        UUID bulkOrg = UUID.randomUUID(); jdbc.update("INSERT INTO organization(id,name) VALUES (?,?)", bulkOrg, "PM目录截断组织");
        Actor reader = createFixture(Identity.STUDENT, Role.APPLICANT);
        jdbc.update("UPDATE app_user SET organization_id=? WHERE id=?", bulkOrg, reader.id());
        String hash = encoder.encode(PASSWORD);
        for (int i = 0; i < 201; i++) jdbc.update("INSERT INTO app_user(id,organization_id,username,display_name,password_hash,identity) VALUES (?,?,?,?,?,'STUDENT')",
                UUID.randomUUID(), bulkOrg, UUID.randomUUID().toString(), "目录测试" + i, hash);
        var response = call("GET", "/users?purpose=staff", login(reader.id().toString()), null, null);
        assertEquals(200, response.data().size()); assertTrue(response.body().path("truncated").asBoolean());
    }
    @Test void unauthorizedWriteDoesNotLeaveReceipt() throws Exception {
        Actor applicant = createFixture(Identity.TEACHER, Role.APPLICANT); UUID key = UUID.randomUUID();
        var result = call("PATCH", "/users/" + applicant.id() + "/enabled", login(applicant.id().toString()), key, new EnabledInput(false));
        assertEquals(403, result.status()); assertTrue(accountPort.get(applicant.id()).enabled());
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM command_receipt WHERE actor_id=? AND idempotency_key=?", Integer.class, applicant.id(), key.toString()));
    }
    @Test void malformedOrMissingFieldsAndKeysReturn400() throws Exception {
        assertEquals(400, call("PATCH", "/users/" + admin.id() + "/enabled", adminToken, null, new EnabledInput(true)).status());
        assertEquals(400, call("PATCH", "/users/" + admin.id() + "/enabled", adminToken, UUID.randomUUID(), Map.of()).status());
        assertEquals(400, call("PATCH", "/users/" + admin.id() + "/enabled", adminToken, UUID.randomUUID(), Map.of("enabled", true, "actorId", UUID.randomUUID())).status());
        assertEquals(400, call("POST", "/auth/login", null, null, Map.of("username", "x")).status());
    }
    @Test void publicHealthAndErrorEnvelopesDoNotLeakInternals() throws Exception {
        assertEquals("UP", call("GET", "/health", null, null, null).data().path("status").asText());
        var error = call("GET", "/auth/me", null, null, null);
        assertEquals(401, error.status()); assertTrue(error.body().has("requestId")); assertFalse(error.body().has("data"));
        assertFalse(error.body().toString().contains("SELECT"));
    }
    @Test void accountLocksAndResourceGrantsRequireExistingTransaction() {
        assertThrows(IllegalTransactionStateException.class, () -> accountPort.lock(admin.id()));
        assertThrows(IllegalTransactionStateException.class, () -> accountPort.grantResource(admin.id(), venue));
        transactions.executeWithoutResult(tx -> { accountPort.lock(admin.id()); accountPort.grantResource(admin.id(), venue); accountPort.grantResource(admin.id(), venue); });
        assertEquals(1, jdbc.queryForObject("SELECT count(*) FROM user_resource_scope WHERE user_id=? AND resource_id=?", Integer.class, admin.id(), venue));
    }
    @Test void requestAndRouteMismatchAreRejectedAndOriginalRemainsReplayable() {
        UUID key = UUID.randomUUID();
        var first = commands.execute(admin, key, "POST /pm-command", "{\"b\":2,\"a\":1}", () -> Responses.success(201, Map.of("ok", true)));
        var replay = commands.execute(admin, key, "POST /pm-command", "{\"a\":1,\"b\":2}", () -> { throw new AssertionError("不应重执行业务"); });
        assertEquals(mapper.valueToTree(first.body()), mapper.valueToTree(replay.body()));
        assertEquals(409, assertThrows(ApiException.class, () -> commands.execute(admin, key, "POST /pm-command", "{}", () -> first)).status());
        assertEquals(409, assertThrows(ApiException.class, () -> commands.execute(admin, key, "POST /other", "{\"a\":1,\"b\":2}", () -> first)).status());
    }
    @Test void concurrentSameKeyRunsBusinessOnce() throws Exception {
        UUID key = UUID.randomUUID(), counter = UUID.randomUUID();
        jdbc.update("INSERT INTO pm_counter(id,value) VALUES (?,0)", counter);
        var calls = new AtomicInteger(); var ready = new CountDownLatch(12); var start = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(12)) {
            var futures = new ArrayList<Future<CommandResult>>();
            for (int i = 0; i < 12; i++) futures.add(pool.submit(() -> {
                ready.countDown(); assertTrue(start.await(10, TimeUnit.SECONDS));
                return commands.execute(admin, key, "POST /concurrent", "{}", () -> {
                    calls.incrementAndGet(); jdbc.update("UPDATE pm_counter SET value=value+1 WHERE id=?", counter);
                    return Responses.success(200, Map.of("counter", 1));
                });
            }));
            assertTrue(ready.await(10, TimeUnit.SECONDS)); start.countDown();
            JsonNode first = null;
            for (var future : futures) {
                JsonNode current = mapper.valueToTree(future.get(30, TimeUnit.SECONDS).body());
                if (first == null) first = current; else assertEquals(first, current);
            }
        }
        assertEquals(1, calls.get()); assertEquals(1, jdbc.queryForObject("SELECT value FROM pm_counter WHERE id=?", Integer.class, counter));
    }
    @Test void exceptionRollsBackBusinessAndReceiptThenAllowsRetry() {
        UUID key = UUID.randomUUID(), counter = UUID.randomUUID(); jdbc.update("INSERT INTO pm_counter(id,value) VALUES (?,0)", counter);
        assertThrows(IllegalStateException.class, () -> commands.execute(admin, key, "POST /rollback", "{}", () -> {
            jdbc.update("UPDATE pm_counter SET value=10 WHERE id=?", counter); throw new IllegalStateException("仅测试故障");
        }));
        assertEquals(0, jdbc.queryForObject("SELECT value FROM pm_counter WHERE id=?", Integer.class, counter));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM command_receipt WHERE actor_id=? AND idempotency_key=?", Integer.class, admin.id(), key.toString()));
        assertEquals(200, commands.execute(admin, key, "POST /rollback", "{}", () -> Responses.success(200, Map.of("ok", true))).httpStatus());
    }
    @Test void conflictResultCommitsAndReplaysOriginalRequestId() {
        UUID key = UUID.randomUUID(), counter = UUID.randomUUID(); jdbc.update("INSERT INTO pm_counter(id,value) VALUES (?,0)", counter);
        var first = commands.execute(admin, key, "POST /conflict", "{}", () -> {
            jdbc.update("UPDATE pm_counter SET value=1 WHERE id=?", counter);
            return Responses.error(409, "RESOURCE_CONFLICT", "资源已占用", Map.of("conflicts", List.of()));
        });
        var second = commands.execute(admin, key, "POST /conflict", "{}", () -> { throw new AssertionError(); });
        assertEquals(409, second.httpStatus()); assertEquals(mapper.valueToTree(first.body()), mapper.valueToTree(second.body()));
        assertEquals(1, jdbc.queryForObject("SELECT value FROM pm_counter WHERE id=?", Integer.class, counter));
    }
    @Test void historyAndAuthorizationRollBackTogether() {
        Actor target = createFixture(Identity.STUDENT, Role.APPLICANT);
        assertThrows(IllegalStateException.class, () -> transactions.executeWithoutResult(tx -> {
            users.replace(admin, target.id(), authorization(Identity.TEACHER, List.of(Role.APPLICANT), List.of(), List.of(), List.of()));
            throw new IllegalStateException("历史写入后的测试故障");
        }));
        assertEquals(Identity.STUDENT, accountPort.get(target.id()).identity()); assertEquals(0, count("authorization_change", target.id()));
    }
    @Test void revokedPermissionCannotBeUsedByStaleActor() {
        Actor manager = createFixture(Identity.TEACHER, Role.ADMIN);
        jdbc.update("INSERT INTO user_permission(user_id,permission) VALUES (?,'USER_MANAGE')", manager.id());
        Actor stale = accountPort.get(manager.id());
        jdbc.update("DELETE FROM user_permission WHERE user_id=?", manager.id());
        var failure = assertThrows(ApiException.class, () -> transactions.executeWithoutResult(tx -> users.enabled(stale, stale.id(), false)));
        assertEquals(403, failure.status()); assertTrue(accountPort.get(stale.id()).enabled());
    }
    @Test void differentKeysForSameActorCanLockUserWithoutDeadlock() throws Exception {
        // 屏障放在action入口：此时两笔独立事务均已插入/锁定不同receipt，外键KEY SHARE均已持有。
        for (int round = 0; round < 5; round++) {
            UUID counter = UUID.randomUUID(); jdbc.update("INSERT INTO pm_counter(id,value) VALUES (?,0)", counter);
            var bothReceiptsInserted = new CyclicBarrier(2);
            List<UUID> keys = List.of(UUID.randomUUID(), UUID.randomUUID());
            try (var pool = Executors.newFixedThreadPool(2)) {
                var futures = new ArrayList<Future<CommandResult>>();
                for (UUID key : keys) futures.add(pool.submit(() -> commands.execute(admin, key, "POST /different-keys", "{}", () -> {
                    awaitBarrier(bothReceiptsInserted);
                    accountPort.lock(admin.id());
                    jdbc.update("UPDATE pm_counter SET value=value+1 WHERE id=?", counter);
                    return Responses.success(200, Map.of("ok", true));
                })));
                for (var future : futures) assertEquals(200, future.get(15, TimeUnit.SECONDS).httpStatus());
            }
            assertEquals(2, jdbc.queryForObject("SELECT value FROM pm_counter WHERE id=?", Integer.class, counter));
            for (UUID key : keys) assertEquals(200, jdbc.queryForObject(
                    "SELECT http_status FROM command_receipt WHERE actor_id=? AND idempotency_key=?", Integer.class, admin.id(), key.toString()));
        }
    }
    @Test void loginUserLockIsCompatibleWithInFlightReceiptForeignKey() throws Exception {
        Actor actor = createFixture(Identity.STUDENT, Role.APPLICANT);
        var receiptInserted = new CountDownLatch(1); var loginCompleted = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var command = pool.submit(() -> commands.execute(actor, UUID.randomUUID(), "POST /login-race", "{}", () -> {
                receiptInserted.countDown();
                try { assertTrue(loginCompleted.await(8, TimeUnit.SECONDS), "登录用户锁应与在途回执外键的KEY SHARE兼容"); }
                catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
                accountPort.lock(actor.id());
                return Responses.success(200, Map.of("ok", true));
            }));
            var login = pool.submit(() -> {
                assertTrue(receiptInserted.await(8, TimeUnit.SECONDS));
                try { return sessions.login(new LoginInput(actor.id().toString(), PASSWORD)); }
                finally { loginCompleted.countDown(); }
            });
            assertNotNull(login.get(15, TimeUnit.SECONDS).token());
            assertEquals(200, command.get(15, TimeUnit.SECONDS).httpStatus());
        }
    }
    private static void awaitBarrier(CyclicBarrier barrier) {
        try { barrier.await(8, TimeUnit.SECONDS); }
        catch (InterruptedException ex) { Thread.currentThread().interrupt(); throw new IllegalStateException(ex); }
        catch (BrokenBarrierException | TimeoutException ex) { throw new IllegalStateException("回执插入屏障超时", ex); }
    }
}
