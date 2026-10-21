package com.campus.events.resource;

import com.campus.events.CampusEventsApplication;
import com.campus.events.contract.*;
import com.campus.events.foundation.*;
import com.campus.events.scheduling.StrategyEvaluator;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.IllegalTransactionStateException;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** 真实PostgreSQL、独立连接/事务与数据库故障注入；不替换生产预约或资源实现。 */
@SpringBootTest(classes = CampusEventsApplication.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class ResourceSchedulingIT {
    static final String SCHEMA = "t_" + UUID.randomUUID().toString().replace("-", "");
    static final Instant START = Instant.parse("2030-10-20T02:00:00Z");
    static String url, username, password;
    @Autowired JdbcTemplate jdbc;
    @Autowired TransactionTemplate tx;
    @Autowired SchedulingPort scheduling;
    @Autowired ResourceRepository resources;
    @Autowired ResourceService service;
    @Autowired AccountPort accounts;
    @Autowired StrategyEvaluator evaluator;
    @Autowired ObjectMapper mapper;
    @Autowired MockMvc http;
    @Autowired PasswordEncoder encoder;
    @Autowired jakarta.validation.Validator validator;
    UUID org, owner, owner2, admin, venue, venue2, equipment;

    static String required(String key) {
        String value = System.getenv(key);
        if (value == null || value.isBlank()) throw new IllegalStateException("真实数据库测试缺少" + key);
        return value;
    }
    @DynamicPropertySource static void database(DynamicPropertyRegistry properties) {
        var missing = List.of("TEST_DATABASE_URL", "TEST_DB_USERNAME", "TEST_DB_PASSWORD", "DEMO_PASSWORD").stream()
                .filter(key -> System.getenv(key) == null || System.getenv(key).isBlank()).toList();
        if (!missing.isEmpty()) throw new IllegalStateException("DS真库HTTP测试阻塞：缺少 " + String.join(", ", missing)
                + "；请在仓库根目录先执行 . ./scripts/load-env.ps1，再运行 Maven verify；不能跳过真库测试记通过");
        url = required("TEST_DATABASE_URL"); username = required("TEST_DB_USERNAME"); password = required("TEST_DB_PASSWORD");
        if (!url.matches("jdbc:postgresql://(?:127\\.0\\.0\\.1|localhost):[0-9]+/campus_test"))
            throw new IllegalStateException("DS测试只允许本机campus_test");
        new JdbcTemplate(new DriverManagerDataSource(url, username, password)).execute("CREATE SCHEMA " + SCHEMA);
        properties.add("spring.datasource.url", () -> url + "?currentSchema=" + SCHEMA);
        properties.add("spring.datasource.username", () -> username);
        properties.add("spring.datasource.password", () -> password);
        properties.add("spring.flyway.schemas", () -> SCHEMA);
        properties.add("spring.flyway.default-schema", () -> SCHEMA);
        properties.add("app.demo-seed", () -> "false");
    }
    @AfterAll void cleanup() {
        if (url != null && SCHEMA.matches("t_[0-9a-f]{32}"))
            new JdbcTemplate(new DriverManagerDataSource(url, username, password)).execute("DROP SCHEMA " + SCHEMA + " CASCADE");
    }
    @BeforeEach void seed() {
        jdbc.execute("TRUNCATE organization,resource CASCADE");
        org = UUID.randomUUID(); owner = UUID.randomUUID(); owner2 = UUID.randomUUID(); admin = UUID.randomUUID();
        jdbc.update("INSERT INTO organization(id,name) VALUES (?,'DS独立测试组织')", org);
        for (UUID id : List.of(owner, owner2, admin))
            jdbc.update("INSERT INTO app_user(id,organization_id,username,display_name,password_hash,identity) VALUES (?,?,?,?,?,'TEACHER')",
                    id, org, id.toString(), "DS测试账号", "不可登录的测试占位");
        jdbc.update("INSERT INTO user_role(user_id,role) VALUES (?,'ADMIN'),(?,'APPLICANT'),(?,'APPLICANT')", admin, owner, owner2);
        jdbc.update("INSERT INTO user_permission(user_id,permission) VALUES (?,'RESOURCE_MANAGE')", admin);
        venue = resource(ResourceKind.VENUE); venue2 = resource(ResourceKind.VENUE); equipment = resource(ResourceKind.EQUIPMENT);
    }
    UUID resource(ResourceKind kind) {
        var created = resources.create(new ResourceCreate(true, true, kind, "DS测试资源",
                kind == ResourceKind.VENUE ? "VENUE" : "PROJECTOR", kind == ResourceKind.VENUE ? 80 : null,
                kind == ResourceKind.VENUE ? 1 : 2, "08:00", "22:00"));
        jdbc.update("INSERT INTO user_resource_scope(user_id,resource_id) VALUES (?,?)", admin, created.id());
        return created.id();
    }
    Plan plan(UUID venueId, int hour, int duration, int quantity) {
        return new Plan(START.plusSeconds(hour * 3600L), START.plusSeconds((hour + duration) * 3600L),
                0, 0, venueId, quantity == 0 ? List.of() : List.of(new EquipmentNeed(equipment, quantity)));
    }
    String json(Object value) {
        try { return mapper.writeValueAsString(value); } catch (Exception e) { throw new IllegalStateException(e); }
    }
    UUID activity(UUID activityOwner, Plan plan) {
        UUID id = UUID.randomUUID();
        tx.executeWithoutResult(status -> {
            jdbc.update("INSERT INTO activity(id,organization_id,owner_id,status,version_no) VALUES (?,?,?,'APPROVED_UNRESERVED',1)",
                    id, org, activityOwner);
            version(id, 1, activityOwner, plan);
        });
        return id;
    }
    void version(UUID id, int number, UUID actor, Plan plan) {
        jdbc.update("INSERT INTO activity_version(activity_id,version_no,input_json,created_by) VALUES (?,?,?::jsonb,?)",
                id, number, json(new ActivityInput("私有活动标题", "", 60, 20, List.of(), plan)), actor);
    }
    void lock(UUID id) {
        jdbc.queryForObject("SELECT id FROM activity WHERE id=? FOR UPDATE", UUID.class, id);
        accounts.lock(resources.activity(id).ownerId());
    }
    boolean book(UUID id, Plan plan) {
        return Boolean.TRUE.equals(tx.execute(status -> {
            lock(id);
            if (!scheduling.lockAndCheck(id, plan, 60, Identity.TEACHER).feasible()) return false;
            scheduling.replaceReservations(id, 1, plan);
            return true;
        }));
    }
    long count(String table) { return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class); }
    String login(UUID id) throws Exception {
        String password = required("DEMO_PASSWORD");
        jdbc.update("UPDATE app_user SET password_hash=? WHERE id=?", encoder.encode(password), id);
        String body = http.perform(post("/api/v1/auth/login").contentType("application/json")
                        .content(json(new LoginInput(id.toString(), password))))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        return mapper.readTree(body).path("data").path("token").asText();
    }
    void fault(UUID resource, Instant start, Instant end, int quantity) {
        tx.executeWithoutResult(s -> {
            resources.lock(List.of(resource));
            resources.addFault(resource, admin, new FaultInput(start, end, quantity, "私有故障原因"));
        });
    }

    @Test void eligibilityMatrixReadsDatabaseForBothResourceKinds() {
        for (UUID restricted : List.of(venue, equipment)) {
            for (boolean teacher : List.of(false, true)) for (boolean student : List.of(false, true)) {
                jdbc.update("UPDATE resource SET allow_teacher=true,allow_student=true");
                jdbc.update("UPDATE resource SET allow_teacher=?,allow_student=? WHERE id=?", teacher, student, restricted);
                for (Identity identity : Identity.values()) {
                    if (identity == Identity.TEACHER ? teacher : student)
                        assertTrue(scheduling.check(plan(venue, 0, 1, 1), 60, null, identity).feasible());
                    else assertEquals("RESOURCE_INELIGIBLE", assertThrows(ApiException.class,
                            () -> scheduling.check(plan(venue, 0, 1, 1), 60, null, identity)).code());
                }
            }
        }
        assertEquals(0, count("reservation"));
    }
    @Test void adjacentLoadsUsePeakAndSetupOverlapConflicts() {
        assertTrue(book(activity(owner, plan(venue, 0, 1, 1)), plan(venue, 0, 1, 1)));
        assertTrue(book(activity(owner2, plan(venue, 1, 1, 1)), plan(venue, 1, 1, 1)));
        assertTrue(scheduling.check(plan(venue2, 0, 2, 1), 60, null, Identity.TEACHER).feasible());
        var next = plan(venue, 2, 1, 0);
        var setup = new Plan(next.startAt(), next.endAt(), 15, 0, venue, List.of());
        var conflicts = scheduling.check(setup, 60, null, Identity.TEACHER).conflicts();
        assertEquals(ConflictCode.VENUE_OCCUPIED, conflicts.getFirst().code());
        assertEquals(next.startAt().minusSeconds(900), conflicts.getFirst().startsAt());
        assertEquals(next.startAt(), conflicts.getFirst().endsAt());
    }
    @Test void twoActivitiesCompeteExactlyOneSucceedsWithoutPartialReservations() throws Exception {
        var p = plan(venue, 0, 1, 2);
        UUID a = activity(owner, p), b = activity(owner2, p);
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return book(a, p); });
            var second = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return book(b, p); });
            assertEquals(1, List.of(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS)).stream().filter(Boolean::booleanValue).count());
        }
        assertEquals(2, count("reservation"));
        assertEquals(1, jdbc.queryForObject("SELECT count(DISTINCT activity_id) FROM reservation WHERE status='ACTIVE'", Integer.class));
    }
    @Test void equipmentCompetitionAcrossVenuesNeverOverbooks() throws Exception {
        var pa = plan(venue, 0, 1, 2); var pb = plan(venue2, 0, 1, 2);
        UUID a = activity(owner, pa), b = activity(owner2, pb);
        var barrier = new CyclicBarrier(2);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return book(a, pa); });
            var second = pool.submit(() -> { barrier.await(10, TimeUnit.SECONDS); return book(b, pb); });
            assertNotEquals(first.get(20, TimeUnit.SECONDS), second.get(20, TimeUnit.SECONDS));
        }
        assertEquals(2, count("reservation"));
        assertEquals(2, jdbc.queryForObject("SELECT sum(quantity) FROM reservation WHERE resource_id=? AND status='ACTIVE'", Integer.class, equipment));
    }
    @Test void latestOwnerIdentityAndEligibilityOverridePreviewArguments() {
        var p = plan(venue, 0, 1, 1);
        UUID id = activity(owner, p);
        assertTrue(scheduling.check(p, 60, id, Identity.TEACHER).feasible());
        jdbc.update("UPDATE resource SET allow_student=false WHERE id=?", equipment);
        jdbc.update("UPDATE app_user SET identity='STUDENT' WHERE id=?", owner);
        assertEquals("RESOURCE_INELIGIBLE", assertThrows(ApiException.class, () -> book(id, p)).code());
        assertEquals(0, count("reservation"));
        jdbc.update("UPDATE app_user SET identity='TEACHER' WHERE id=?", owner);
        assertTrue(book(id, p));
        tx.executeWithoutResult(s -> service.eligibility(accounts.get(admin), equipment, new ResourceEligibility(false, false)));
        assertEquals(2, resources.active(id).size());
        assertEquals(Risk.NORMAL, scheduling.risk(id));
        assertEquals(1, count("resource_eligibility_change"));
        assertTrue(jdbc.queryForObject("SELECT before_json->>'allowTeacher' FROM resource_eligibility_change", Boolean.class));
        assertFalse(jdbc.queryForObject("SELECT after_json->>'allowTeacher' FROM resource_eligibility_change", Boolean.class));
    }
    @Test void replaceExcludesOwnOccupancyAndReleaseIsRepeatable() {
        var old = plan(venue, 0, 2, 1);
        UUID id = activity(owner, old);
        assertTrue(book(id, old));
        var target = plan(venue2, 1, 2, 2);
        tx.executeWithoutResult(s -> {
            lock(id);
            assertTrue(scheduling.lockAndCheck(id, target, 60, Identity.TEACHER).feasible());
            version(id, 2, owner, target);
            scheduling.replaceReservations(id, 2, target);
        });
        assertEquals(4, count("reservation"));
        assertEquals(2, resources.active(id).size());
        assertTrue(resources.active(id).stream().allMatch(r -> r.versionNo() == 2));
        tx.executeWithoutResult(s -> { lock(id); scheduling.releaseReservations(id); scheduling.releaseReservations(id); });
        assertEquals(0, resources.active(id).size());
    }
    @Test void injectedDatabaseFailureRollsBackReleasePartialInsertAndVersion() {
        var old = plan(venue, 0, 1, 1);
        UUID id = activity(owner, old);
        assertTrue(book(id, old));
        var before = resources.active(id);
        jdbc.execute("""
                CREATE FUNCTION ds_fail_reservation() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN IF NEW.version_no=2 AND NEW.quantity=2 THEN RAISE EXCEPTION 'DS测试预约中途失败'; END IF; RETURN NEW; END $$
                """);
        jdbc.execute("CREATE TRIGGER ds_fail BEFORE INSERT ON reservation FOR EACH ROW EXECUTE FUNCTION ds_fail_reservation()");
        try {
            var target = plan(venue2, 1, 1, 2);
            assertThrows(RuntimeException.class, () -> tx.executeWithoutResult(s -> {
                lock(id);
                assertTrue(scheduling.lockAndCheck(id, target, 60, Identity.TEACHER).feasible());
                version(id, 2, owner, target);
                jdbc.update("UPDATE activity SET version_no=2,effective_version_no=2 WHERE id=?", id);
                scheduling.replaceReservations(id, 2, target);
            }));
            assertEquals(before, resources.active(id));
            assertEquals(2, count("reservation"));
            assertEquals(1, count("activity_version"));
            assertEquals(1, resources.activity(id).versionNo());
        } finally {
            jdbc.execute("DROP TRIGGER ds_fail ON reservation");
            jdbc.execute("DROP FUNCTION ds_fail_reservation()");
        }
    }
    @Test void conflictAndMissingResourceLeaveNoPartialReservation() {
        var occupied = plan(venue, 0, 2, 2);
        assertTrue(book(activity(owner, occupied), occupied));
        var p = plan(venue2, 1, 2, 1);
        UUID id = activity(owner2, p);
        assertFalse(book(id, p));
        assertTrue(resources.active(id).isEmpty());
        var result = scheduling.check(p, 60, id, Identity.TEACHER);
        var conflict = result.conflicts().getFirst();
        assertEquals(ConflictCode.EQUIPMENT_SHORTAGE, conflict.code());
        assertEquals(3, conflict.required()); assertEquals(2, conflict.available());
        assertEquals(START.plusSeconds(3600), conflict.startsAt());
        var missing = new Plan(p.startAt(), p.endAt(), 0, 0, venue2, List.of(new EquipmentNeed(UUID.randomUUID(), 1)));
        assertThrows(ApiException.class, () -> tx.executeWithoutResult(s -> { lock(id); scheduling.lockAndCheck(id, missing, 60, Identity.TEACHER); }));
        assertEquals(2, count("reservation"));
    }
    @Test void mandatoryTransactionAndSuccessfulSamePlanCheckAreRequired() {
        var p = plan(venue, 0, 1, 1); UUID id = activity(owner, p);
        assertThrows(IllegalTransactionStateException.class, () -> scheduling.lockAndCheck(id, p, 60, Identity.TEACHER));
        assertThrows(IllegalTransactionStateException.class, () -> scheduling.releaseReservations(id));
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> scheduling.replaceReservations(id, 1, p)));
        tx.executeWithoutResult(s -> { lock(id); assertTrue(scheduling.lockAndCheck(id, p, 60, Identity.TEACHER).feasible()); });
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> scheduling.replaceReservations(id, 1, p)));
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s -> {
            lock(id); scheduling.lockAndCheck(id, p, 60, Identity.TEACHER);
            scheduling.replaceReservations(id, 1, plan(venue2, 0, 1, 1));
        }));
        assertEquals(0, count("reservation"));
    }
    @Test void overlappingFaultsClampCapacityAndRiskIsLive() {
        var p = plan(venue, 0, 1, 1); UUID id = activity(owner, p);
        assertTrue(book(id, p));
        fault(equipment, p.startAt(), p.endAt(), 2);
        fault(equipment, p.startAt(), p.endAt(), 2);
        assertEquals(Risk.AT_RISK, scheduling.risk(id));
        var conflict = scheduling.check(p, 60, id, Identity.TEACHER).conflicts().getFirst();
        assertEquals(ConflictCode.RESOURCE_FAULT, conflict.code());
        assertEquals(0, conflict.available());
        assertEquals(2, resources.active(id).size());
        jdbc.update("INSERT INTO user_permission(user_id,permission) VALUES (?,'RESOURCE_CONFIRM')", admin);
        @SuppressWarnings("unchecked") var body = (Map<String, Object>) service.faults(accounts.get(admin), equipment, p.startAt(), p.endAt()).body();
        var views = (List<?>) body.get("data");
        assertEquals(id, ((FaultView) views.getFirst()).affectedActivities().getFirst().activityId());
        assertEquals(Risk.AT_RISK, ((FaultView) views.getFirst()).affectedActivities().getFirst().resourceRisk());
    }
    @Test void resourceCreateGrantsScopeAndRangeViolationsDoNotWrite() {
        Actor actor = accounts.get(admin);
        var input = new ResourceCreate(false, true, ResourceKind.EQUIPMENT, "新设备", "PROJECTOR", null, 2, "08:00", "22:00");
        var created = tx.execute(s -> service.create(actor, input));
        assertTrue(accounts.get(admin).managedResourceIds().contains(created.id()));
        jdbc.update("DELETE FROM user_resource_scope WHERE user_id=? AND resource_id=?", admin, created.id());
        assertEquals(403, assertThrows(ApiException.class, () -> tx.executeWithoutResult(s ->
                service.eligibility(actor, created.id(), new ResourceEligibility(true, true)))).status());
        assertFalse(resources.get(created.id()).allowTeacher());
        assertEquals(0, count("resource_eligibility_change"));
        assertEquals(403, assertThrows(ApiException.class, () -> tx.executeWithoutResult(s -> service.create(accounts.get(owner), input))).status());
    }
    @Test void calendarSegmentsAreRedactedAndHttpUsesCommonEnvelope() throws Exception {
        var p = new Plan(START, START.plusSeconds(3600), 30, 30, venue, List.of());
        UUID id = activity(owner, p); assertTrue(book(id, p));
        fault(venue, START, START.plusSeconds(1800), 1);
        String token = login(owner2);
        var response = http.perform(get("/api/v1/resources/calendar")
                        .param("from", START.minusSeconds(1800).toString()).param("to", START.plusSeconds(5400).toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.requestId").isNotEmpty()).andReturn().getResponse().getContentAsString();
        assertFalse(response.contains("私有"));
        var entries = mapper.readTree(response).path("data");
        assertEquals(4, entries.size());
        var segments = new HashSet<String>();
        entries.forEach(e -> {
            assertTrue(e.path("activityId").isNull());
            String segment = e.path("segment").asText();
            segments.add(segment);
            assertEquals(segment.equals("FAULT") ? "AT_RISK" : "NORMAL", e.path("risk").asText());
            assertEquals(segment.equals("FAULT") ? "资源不可用" : "已占用", e.path("label").asText());
        });
        assertEquals(Set.of("SETUP", "EVENT", "TEARDOWN", "FAULT"), segments);
        assertEquals(Risk.AT_RISK, scheduling.risk(id));
        String ownerResponse = http.perform(get("/api/v1/resources/calendar")
                        .param("from", START.minusSeconds(1800).toString()).param("to", START.plusSeconds(5400).toString())
                        .header("Authorization", "Bearer " + login(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString();
        mapper.readTree(ownerResponse).path("data").forEach(e -> {
            if (!e.path("segment").asText().equals("FAULT")) {
                assertEquals(id.toString(), e.path("activityId").asText());
                assertEquals("私有活动标题", e.path("label").asText());
                assertEquals("AT_RISK", e.path("risk").asText());
            }
        });
        http.perform(get("/api/v1/resources/calendar").param("from", START.toString()).param("to", START.plusSeconds(8 * 86400).toString())
                .header("Authorization", "Bearer " + token)).andExpect(status().isBadRequest());
    }

    @Test void faultHttpListFiltersEachActivityAndRechecksCurrentScope() throws Exception {
        var firstPlan = plan(venue, 0, 1, 1);
        var secondPlan = plan(venue2, 0, 1, 1);
        UUID first = activity(owner, firstPlan), second = activity(owner2, secondPlan);
        assertTrue(book(first, firstPlan)); assertTrue(book(second, secondPlan));
        fault(equipment, START, START.plusSeconds(3600), 2);
        String token = login(admin);
        String hidden = faultList(token);
        assertEquals(0, mapper.readTree(hidden).path("data").get(0).path("affectedActivities").size());
        assertFalse(hidden.contains(first.toString())); assertFalse(hidden.contains(second.toString()));
        assertFalse(hidden.contains("私有活动标题")); assertFalse(hidden.contains("versionNo"));
        assertFalse(hidden.contains("resourceRisk")); assertFalse(hidden.contains("AT_RISK"));

        jdbc.update("INSERT INTO user_permission(user_id,permission) VALUES (?,'RESOURCE_CONFIRM')", admin);
        jdbc.update("DELETE FROM user_resource_scope WHERE user_id=? AND resource_id=?", admin, venue2);
        var visible = mapper.readTree(faultList(token)).path("data").get(0).path("affectedActivities");
        assertEquals(1, visible.size());
        assertEquals(first.toString(), visible.get(0).path("activityId").asText());
        assertEquals("私有活动标题", visible.get(0).path("title").asText());
        assertEquals(1, visible.get(0).path("versionNo").asInt());
        assertEquals("AT_RISK", visible.get(0).path("resourceRisk").asText());

        jdbc.update("INSERT INTO user_resource_scope(user_id,resource_id) VALUES (?,?)", admin, venue2);
        assertEquals(2, mapper.readTree(faultList(token)).path("data").get(0).path("affectedActivities").size());
        jdbc.update("DELETE FROM user_permission WHERE user_id=? AND permission='RESOURCE_CONFIRM'", admin);
        assertEquals(0, mapper.readTree(faultList(token)).path("data").get(0).path("affectedActivities").size());
        assertEquals(1, count("resource_fault")); assertEquals(4, count("reservation"));
        assertEquals(Risk.AT_RISK, scheduling.risk(first)); assertEquals(Risk.AT_RISK, scheduling.risk(second));
        http.perform(get("/api/v1/resources/" + equipment + "/faults")
                        .param("from", START.toString()).param("to", START.plusSeconds(3600).toString())
                        .header("Authorization", "Bearer " + login(owner)))
                .andExpect(status().isForbidden());
    }

    String faultList(String token) throws Exception {
        return http.perform(get("/api/v1/resources/" + equipment + "/faults")
                        .param("from", START.toString()).param("to", START.plusSeconds(3600).toString())
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk()).andExpect(jsonPath("$.truncated").value(false))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andReturn().getResponse().getContentAsString();
    }

    @Test void faultResponseRedactionDoesNotSuppressInternalActivityEvents() {
        var p = plan(venue, 0, 1, 1);
        UUID id = activity(owner, p); assertTrue(book(id, p));
        var emitted = new ArrayList<EventDraft>();
        var dependencies = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        // 仅记录端口调用的测试替身，不代表CM事件持久化已经验收。
        dependencies.addBean("events", (EventPort) event -> { emitted.add(event); return UUID.randomUUID(); });
        var isolated = new ResourceService(resources, accounts, scheduling, dependencies.getBeanProvider(EventPort.class),
                new CanonicalJson(mapper), validator);
        FaultView response = tx.execute(s -> isolated.fault(accounts.get(admin), equipment,
                new FaultInput(START, START.plusSeconds(3600), 2, "测试故障")));
        assertTrue(response.affectedActivities().isEmpty());
        assertEquals(1, emitted.size());
        assertEquals(id, emitted.getFirst().activityId());
        assertEquals(1, emitted.getFirst().versionNo());
        assertEquals(List.of(owner), emitted.getFirst().recipientIds());
        assertEquals(1, count("resource_fault")); assertEquals(2, resources.active(id).size());
        assertEquals(Risk.AT_RISK, scheduling.risk(id));
    }
    @Test void resourceListsAndCalendarUseStableCreationOrder() {
        jdbc.update("UPDATE resource SET created_at=?", Timestamp.from(START));
        jdbc.update("UPDATE resource SET created_at=? WHERE id=?", Timestamp.from(START.plusSeconds(60)), equipment);
        var venues = List.of(venue, venue2).stream().sorted(Comparator.comparing(UUID::toString)).toList();
        assertEquals(List.of(equipment, venues.get(0), venues.get(1)),
                resources.list(null, null).stream().map(ResourceView::id).toList());

        var p = new Plan(START, START.plusSeconds(3600), 30, 30, venue, List.of());
        var q = new Plan(START.plusSeconds(7200), START.plusSeconds(10800), 30, 30, venue2, List.of());
        UUID first = activity(owner, p), second = activity(owner2, q);
        assertTrue(book(first, p)); assertTrue(book(second, q));
        UUID faultId = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID secondId = UUID.fromString("00000000-0000-0000-0000-000000000002");
        UUID firstId = UUID.fromString("00000000-0000-0000-0000-000000000003");
        jdbc.update("UPDATE reservation SET id=?,created_at=? WHERE activity_id=?", firstId, Timestamp.from(START.minusSeconds(60)), first);
        jdbc.update("UPDATE reservation SET id=?,created_at=? WHERE activity_id=?", secondId, Timestamp.from(START), second);
        fault(venue, START, START.plusSeconds(60), 1);
        jdbc.update("UPDATE resource_fault SET id=?,created_at=?", faultId, Timestamp.from(START));
        var expected = new ArrayList<ResourceRepository.CalendarRow>();
        expected.add(new ResourceRepository.CalendarRow(venue, START, START.plusSeconds(60), 1, null, null, CalendarSegment.FAULT));
        // 相同createdAt按源记录UUID；同一预约再按分段开始时间排列。
        for (var entry : List.of(Map.entry(second, q), Map.entry(first, p))) {
            var plan = entry.getValue();
            expected.add(new ResourceRepository.CalendarRow(plan.venueId(), plan.startAt().minusSeconds(1800), plan.startAt(), 1,
                    entry.getKey(), 1, CalendarSegment.SETUP));
            expected.add(new ResourceRepository.CalendarRow(plan.venueId(), plan.startAt(), plan.endAt(), 1,
                    entry.getKey(), 1, CalendarSegment.EVENT));
            expected.add(new ResourceRepository.CalendarRow(plan.venueId(), plan.endAt(), plan.endAt().plusSeconds(1800), 1,
                    entry.getKey(), 1, CalendarSegment.TEARDOWN));
        }
        assertEquals(expected, resources.calendar(START.minusSeconds(1800), START.plusSeconds(12600), null));

        fault(venue, START.plusSeconds(60), START.plusSeconds(120), 1);
        fault(venue, START.plusSeconds(120), START.plusSeconds(180), 1);
        jdbc.update("UPDATE resource_fault SET created_at=?", Timestamp.from(START));
        jdbc.update("UPDATE resource_fault SET created_at=? WHERE id=?", Timestamp.from(START.plusSeconds(60)), faultId);
        var others = jdbc.queryForList("SELECT id FROM resource_fault WHERE id<>? ORDER BY id", UUID.class, faultId);
        assertEquals(List.of(faultId, others.get(0), others.get(1)),
                resources.listFaults(venue, START, START.plusSeconds(3600)).stream().map(ResourceRepository.Fault::id).toList());
    }

    @Test void calendarTruncationAndCandidateBudgetUseRealRows() throws Exception {
        for (int i = 0; i < 201; i++) fault(venue, START, START.plusSeconds(60), 1);
        @SuppressWarnings("unchecked") var result = (Map<String, Object>) service.calendar(accounts.get(owner2), START, START.plusSeconds(3600), venue).body();
        assertEquals(true, result.get("truncated"));
        assertEquals(200, ((List<?>) result.get("data")).size());
        for (int i = 0; i < 36; i++) resource(ResourceKind.VENUE);
        var alternatives = scheduling.alternatives(plan(venue2, 0, 1, 1), 60, null, Identity.TEACHER);
        assertEquals(300, alternatives.evaluatedCount());
        assertTrue(alternatives.truncated());
        assertEquals(5, alternatives.items().size());
        assertEquals(0, count("reservation"));
        var strategies = evaluator.evaluateStrategies(plan(venue2, 0, 1, 1), 60, null, Identity.TEACHER);
        assertTrue(strategies.stream().allMatch(r -> r.feasible() && r.elapsedNanos() > 0));
        // 无解场景让两种策略都实际检查满300个完整组合，丢弃预热后记录十轮真实耗时。
        var requested = plan(venue2, 0, 1, 1);
        evaluator.evaluateStrategies(requested, 10000, null, Identity.TEACHER);
        var measured = new ArrayList<Map<String, Object>>();
        for (int run = 1; run <= 10; run++) {
            for (var resultRow : evaluator.evaluateStrategies(requested, 10000, null, Identity.TEACHER)) {
                assertFalse(resultRow.feasible());
                assertNull(resultRow.choice());
                assertEquals(300, resultRow.evaluatedCount());
                assertTrue(resultRow.elapsedNanos() > 0);
                measured.add(Map.of("runIndex", run, "result", resultRow));
            }
        }
        var destination = java.nio.file.Path.of("target", "ds-budget-evaluation.json");
        java.nio.file.Files.createDirectories(destination.getParent());
        mapper.writerWithDefaultPrettyPrinter().writeValue(destination.toFile(), Map.of(
                "scenario", "DS-300-无解预算", "database", "PostgreSQL 17", "identity", Identity.TEACHER,
                "attendees", 10000, "plan", requested, "resources", resources.all(), "warmupRuns", 1, "runs", measured));
    }
    @Test void absentEventPortDoesNotCommitFault() {
        // 只在本用例构造空依赖容器；不依赖main是否已经合入CM，也不注册生产替身。
        var noEvents = new org.springframework.beans.factory.support.StaticListableBeanFactory().getBeanProvider(EventPort.class);
        var isolated = new ResourceService(resources, accounts, scheduling, noEvents, new CanonicalJson(mapper), validator);
        var error = assertThrows(ApiException.class, () -> tx.executeWithoutResult(s ->
                isolated.fault(accounts.get(admin), venue, new FaultInput(START, START.plusSeconds(60), 1, "测试故障"))));
        assertEquals(503, error.status());
        assertEquals(0, count("resource_fault"));
    }

    @Test void eventFailureRollsBackFaultAndLeavesOriginalReservations() {
        var p = plan(venue, 0, 1, 1);
        UUID id = activity(owner, p);
        assertTrue(book(id, p));
        var dependencies = new org.springframework.beans.factory.support.StaticListableBeanFactory();
        dependencies.addBean("events", (EventPort) event -> { throw new IllegalStateException("测试事件写入失败"); });
        var isolated = new ResourceService(resources, accounts, scheduling, dependencies.getBeanProvider(EventPort.class),
                new CanonicalJson(mapper), validator);
        assertThrows(IllegalStateException.class, () -> tx.executeWithoutResult(s ->
                isolated.fault(accounts.get(admin), venue, new FaultInput(START, START.plusSeconds(60), 1, "测试故障"))));
        assertEquals(0, count("resource_fault"));
        assertEquals(2, resources.active(id).size());
        assertEquals(Risk.NORMAL, scheduling.risk(id));
    }

    @Test void resourceHttpWritesReuseSharedIdempotencyAndPersistScopeAndHistory() throws Exception {
        String token = login(admin);
        String key = UUID.randomUUID().toString();
        String input = json(new ResourceCreate(true, false, ResourceKind.VENUE, "HTTP场地", "VENUE", 90, 1, "08:00", "22:00"));
        String first = http.perform(post("/api/v1/resources").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key).contentType("application/json").content(input))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        String replay = http.perform(post("/api/v1/resources").header("Authorization", "Bearer " + token)
                        .header("Idempotency-Key", key).contentType("application/json").content(input))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString();
        assertEquals(mapper.readTree(first), mapper.readTree(replay));
        UUID created = UUID.fromString(mapper.readTree(first).path("data").path("id").asText());
        assertTrue(accounts.get(admin).managedResourceIds().contains(created));
        assertEquals(4, count("resource"));
        String eligibilityKey = UUID.randomUUID().toString();
        for (int i = 0; i < 2; i++)
            http.perform(patch("/api/v1/resources/" + created + "/eligibility").header("Authorization", "Bearer " + token)
                            .header("Idempotency-Key", eligibilityKey).contentType("application/json")
                            .content("{\"allowTeacher\":false,\"allowStudent\":true}"))
                    .andExpect(status().isOk()).andExpect(jsonPath("$.data.allowTeacher").value(false));
        assertEquals(1, count("resource_eligibility_change"));
        assertEquals(2, count("command_receipt"));
        http.perform(post("/api/v1/resources").header("Authorization", "Bearer " + token).contentType("application/json").content(input))
                .andExpect(status().isBadRequest());
        assertEquals(4, count("resource"));
    }

    @Test void finalCheckWaitsForEligibilityWriterAndReadsCommittedRestriction() throws Exception {
        var p = plan(venue, 0, 1, 1);
        UUID id = activity(owner, p);
        var locked = new CountDownLatch(1);
        var releaseWriter = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var writer = pool.submit(() -> tx.executeWithoutResult(s -> {
                service.eligibility(accounts.get(admin), equipment, new ResourceEligibility(false, true));
                locked.countDown();
                try { assertTrue(releaseWriter.await(15, TimeUnit.SECONDS)); }
                catch (InterruptedException e) { Thread.currentThread().interrupt(); throw new IllegalStateException(e); }
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var booking = pool.submit(() -> assertThrows(ApiException.class, () -> book(id, p)));
            try {
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                boolean waiting = false;
                while (!waiting && System.nanoTime() < deadline) {
                    waiting = Boolean.TRUE.equals(jdbc.queryForObject("""
                            SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database()
                            AND wait_event_type='Lock' AND query LIKE 'SELECT id FROM resource WHERE id=%FOR UPDATE%')
                            """, Boolean.class));
                    if (!waiting) java.util.concurrent.locks.LockSupport.parkNanos(TimeUnit.MILLISECONDS.toNanos(10));
                }
                assertTrue(waiting, "最终确认应等待资格修改持有的同一资源行锁");
            } finally { releaseWriter.countDown(); }
            writer.get(15, TimeUnit.SECONDS);
            assertEquals("RESOURCE_INELIGIBLE", booking.get(15, TimeUnit.SECONDS).code());
        }
        assertEquals(0, count("reservation"));
    }
}
