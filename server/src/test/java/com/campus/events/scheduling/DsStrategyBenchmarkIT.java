package com.campus.events.scheduling;

import com.campus.events.contract.*;
import com.campus.events.resource.ResourceRepository;
import com.campus.events.support.DbTestSupport;
import com.fasterxml.jackson.databind.*;
import com.fasterxml.jackson.databind.node.*;
import jakarta.validation.Validation;
import java.nio.file.*;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import org.springframework.transaction.support.TransactionTemplate;
import static org.junit.jupiter.api.Assertions.*;

/**
 * DS独立真库评测：复用QA场景文件及02§8接口，不修改/替代SchedulingBenchmarkIT。
 * full-reference与QA声明的全部参考资源一致；scenario-core是显式的四资源对照实验。
 * 两种环境分别输出，完整保留参考数据扩充造成的预期偏差，不能混记为正式QA通过。
 */
class DsStrategyBenchmarkIT {
    @RegisterExtension static final DbTestSupport db = DbTestSupport.create();
    static final ObjectMapper JSON = new ObjectMapper().findAndRegisterModules();
    static final String DATE = "2030-10-20";
    static final UUID OWNER = uuid(2001), ADMIN = uuid(2004), ORG = uuid(1001);

    @Configuration @EnableTransactionManagement
    static class Transactions {}

    @Test void fullReferenceDataRecordsActualResultsAndExpectedDifferences() throws Exception {
        benchmark(false);
    }

    @Test void fourResourceControlMatchesTheEightScenarioExpectations() throws Exception {
        benchmark(true);
    }

    private void benchmark(boolean coreOnly) throws Exception {
        Path data = Path.of(System.getProperty("test.data.dir", "../test-data"));
        JsonNode demo = read(data.resolve("demo.json"));
        JsonNode input = read(data.resolve("scenarios/scheduling.json"));
        assertEquals(8, input.path("scenarios").size());
        assertEquals(1, input.path("evaluation").path("warmupRuns").asInt());
        assertEquals(10, input.path("evaluation").path("measuredRuns").asInt());
        assertEquals(CandidateSearch.MAX_EVALUATIONS, input.path("evaluation").path("maxCandidates").asInt());
        assertEquals(CandidateRanking.OFFSETS_MINUTES,
                JSON.convertValue(input.path("evaluation").path("offsetsMinutes"),
                        new com.fasterxml.jackson.core.type.TypeReference<List<Integer>>() {}));

        var transactionManager = new DataSourceTransactionManager(db.dataSource());
        var tx = new TransactionTemplate(transactionManager);
        try (var validators = Validation.buildDefaultValidatorFactory();
             var context = new AnnotationConfigApplicationContext()) {
            var repository = new ResourceRepository(db.jdbc(), JSON);
            var checker = new ConstraintChecker(repository, validators.getValidator());
            context.register(Transactions.class);
            context.registerBean("transactionManager", DataSourceTransactionManager.class, () -> transactionManager);
            context.registerBean(CandidateSearch.class, () -> new CandidateSearch(repository, checker));
            context.registerBean(StrategyEvaluator.class);
            context.refresh();
            var evaluator = context.getBean(StrategyEvaluator.class);
            assertTrue(org.springframework.aop.support.AopUtils.isAopProxy(evaluator), "评测必须经过快照事务代理");

            loadReferences(demo, coreOnly);
            ArrayNode runs = JSON.createArrayNode(), differences = JSON.createArrayNode();
            for (JsonNode scenario : input.path("scenarios")) {
                loadScenario(scenario, tx);
                long before = count("reservation");
                var request = scenario.path("request");
                var plan = JSON.treeToValue(request.path("plan"), Plan.class);
                int attendees = request.path("expectedAttendees").asInt();
                UUID excluded = uuid(request.path("subjectTail").asLong());
                evaluator.evaluateStrategies(plan, attendees, excluded, Identity.TEACHER); // 预热丢弃
                Map<String, String> signatures = new HashMap<>();
                for (int run = 1; run <= 10; run++) {
                    var results = evaluator.evaluateStrategies(plan, attendees, excluded, Identity.TEACHER);
                    assertEquals(List.of("FIRST_FIT", "RANKED"), results.stream().map(StrategyResult::strategy).toList());
                    for (var result : results) {
                        assertEquals(result.choice() != null, result.feasible());
                        assertTrue(result.evaluatedCount() >= 0 && result.evaluatedCount() <= 300);
                        assertTrue(result.elapsedNanos() > 0);
                        if (result.feasible()) assertTrue(checker.check(result.choice().plan(), attendees, excluded, Identity.TEACHER).feasible());
                        ObjectNode row = row(scenario.path("scenarioId").asText(), run, result);
                        ObjectNode signature = row.deepCopy();
                        signature.remove(List.of("runIndex", "elapsedMs"));
                        String previous = signatures.putIfAbsent(result.strategy(), signature.toString());
                        if (previous != null) assertEquals(previous, signature.toString(), "同一场景选择必须稳定");
                        runs.add(row);
                        if (run == 1 && !matchesExpected(scenario.path("expected"), result)) {
                            ObjectNode mismatch = row.deepCopy();
                            mismatch.set("expected", scenario.path("expected"));
                            differences.add(mismatch);
                        }
                    }
                }
                assertEquals(before, count("reservation"), "评测不得写预约");
            }
            assertEquals(160, runs.size());
            String mode = coreOnly ? "scenario-core" : "full-reference";
            ObjectNode output = JSON.createObjectNode();
            output.put("schemaVersion", 1).put("generatedAt", Instant.now().toString());
            output.put("entrypoint", "DsStrategyBenchmarkIT（DS独立路径，非QA正式入口）");
            output.put("resourceUniverse", mode).put("identity", "TEACHER").put("warmupRuns", 1).put("measuredRuns", 10);
            output.put("resourceCount", count("resource"));
            output.put("timingScope", "候选生成、资源读取、约束检查及选择/排序；不含HTTP与文件输出");
            var environment = output.putObject("environment");
            environment.put("database", db.jdbc().queryForObject("SELECT version()", String.class));
            environment.put("schema", db.schemaName()).put("jdk", System.getProperty("java.version"));
            environment.put("os", System.getProperty("os.name") + "/" + System.getProperty("os.arch"));
            environment.put("baseCommit", System.getenv().getOrDefault("GIT_COMMIT", "未提供"));
            output.set("runs", runs);
            output.set("expectedDifferences", differences);
            Path out = Path.of(System.getProperty("test.output.dir", "target"), "ds-strategy-" + mode + ".json");
            Files.createDirectories(out.toAbsolutePath().getParent());
            JSON.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), output);
            if (coreOnly) assertEquals(0, differences.size(), "四资源对照应符合现有八场景预期，详见原始结果");
        }
    }

    private static void loadReferences(JsonNode demo, boolean coreOnly) {
        db.jdbc().execute("TRUNCATE organization,resource CASCADE"); // 仅本类随机schema
        for (var org : demo.path("organizations"))
            db.jdbc().update("INSERT INTO organization(id,name) VALUES (?::uuid,?)", org.path("id").asText(), org.path("name").asText());
        for (var user : demo.path("users"))
            db.jdbc().update("""
                    INSERT INTO app_user(id,organization_id,username,display_name,password_hash,identity)
                    VALUES (?::uuid,?::uuid,?,?,'不可登录的测试占位',?)
                    """, user.path("id").asText(), user.path("organizationId").asText(),
                    user.path("username").asText(), user.path("displayName").asText(), user.path("identity").asText());
        var coreIds = Set.of(uuid(3101), uuid(3102), uuid(3201), uuid(3202));
        for (var resource : demo.path("resources")) {
            if (coreOnly && !coreIds.contains(UUID.fromString(resource.path("id").asText()))) continue;
            db.jdbc().update("""
                    INSERT INTO resource(id,kind,name,category,capacity,quantity,open_from,open_to,allow_teacher,allow_student)
                    VALUES (?::uuid,?,?,?,?,?,?::time,?::time,?,?)
                    """, resource.path("id").asText(), resource.path("kind").asText(), resource.path("name").asText(),
                    resource.path("category").asText(), resource.path("capacity").isNull() ? null : resource.path("capacity").asInt(),
                    resource.path("quantity").asInt(), resource.path("openFrom").asText(), resource.path("openTo").asText(),
                    resource.path("allowTeacher").asBoolean(), resource.path("allowStudent").asBoolean());
        }
        assertEquals(coreOnly ? 4 : demo.path("resources").size(), count("resource"));
    }

    private static void loadScenario(JsonNode scenario, TransactionTemplate tx) {
        db.jdbc().execute("TRUNCATE activity,resource_fault CASCADE");
        tx.executeWithoutResult(status -> {
            for (var blocker : scenario.path("blockers")) {
                UUID id = uuid(blocker.path("tail").asLong());
                var reservations = blocker.path("reservations");
                Instant start = null, end = null;
                String venue = scenario.path("request").path("plan").path("venueId").asText();
                for (var reservation : reservations) {
                    var from = instant(reservation, "startAt"); var to = instant(reservation, "endAt");
                    if (start == null || from.isBefore(start)) start = from;
                    if (end == null || to.isAfter(end)) end = to;
                    String resource = reservation.path("resourceId").asText();
                    if ("VENUE".equals(db.jdbc().queryForObject("SELECT kind FROM resource WHERE id=?::uuid", String.class, resource))) venue = resource;
                }
                var plan = new Plan(start, end, 0, 0, UUID.fromString(venue), List.of());
                var activity = new ActivityInput(blocker.path("title").asText(), "策略评测占用数据", 1, 1, List.of(), plan);
                db.jdbc().update("INSERT INTO activity(id,organization_id,owner_id,status,version_no,effective_version_no) VALUES (?,?,?,'SCHEDULED',1,1)", id, ORG, OWNER);
                db.jdbc().update("INSERT INTO activity_version(activity_id,version_no,input_json,created_by) VALUES (?,1,?::jsonb,?)", id, JSON.valueToTree(activity).toString(), OWNER);
                for (var reservation : reservations)
                    db.jdbc().update("""
                            INSERT INTO reservation(id,activity_id,version_no,resource_id,start_at,end_at,quantity,status)
                            VALUES (?,?,1,?::uuid,?,?,?,'ACTIVE')
                            """, UUID.randomUUID(), id, reservation.path("resourceId").asText(),
                            Timestamp.from(instant(reservation, "startAt")), Timestamp.from(instant(reservation, "endAt")), reservation.path("quantity").asInt());
            }
            for (var fault : scenario.path("faults"))
                db.jdbc().update("""
                        INSERT INTO resource_fault(id,resource_id,start_at,end_at,unavailable_quantity,reason,created_by)
                        VALUES (?,?::uuid,?,?,?,?,?)
                        """, UUID.randomUUID(), fault.path("resourceId").asText(), Timestamp.from(instant(fault, "startAt")),
                        Timestamp.from(instant(fault, "endAt")), fault.path("unavailableQuantity").asInt(), fault.path("reason").asText(), ADMIN);
        });
        long reservations = 0;
        for (var blocker : scenario.path("blockers")) reservations += blocker.path("reservations").size();
        assertEquals(scenario.path("blockers").size(), count("activity"));
        assertEquals(reservations, count("reservation"));
        assertEquals(scenario.path("faults").size(), count("resource_fault"));
    }

    private static ObjectNode row(String scenario, int run, StrategyResult result) {
        var row = JSON.createObjectNode().put("scenarioId", scenario).put("strategy", result.strategy())
                .put("runIndex", run).put("feasible", result.feasible()).put("evaluatedCount", result.evaluatedCount())
                .put("elapsedMs", result.elapsedNanos() / 1_000_000.0);
        if (result.choice() == null) {
            row.putNull("timeShiftMinutes").putNull("venueChanged").putNull("replacedEquipmentLines");
            row.putNull("choice");
        } else {
            var choice = result.choice();
            row.put("timeShiftMinutes", choice.timeShiftMinutes()).put("venueChanged", choice.venueChanged())
                    .put("replacedEquipmentLines", choice.replacedEquipmentLines());
            row.set("choice", JSON.valueToTree(choice));
        }
        return row;
    }

    private static boolean matchesExpected(JsonNode expected, StrategyResult result) {
        if (result.feasible() != expected.path("feasible").asBoolean()) return false;
        var choice = expected.path(result.strategy().equals("FIRST_FIT") ? "firstFitChoice" : "rankedChoice");
        if (choice.isNull()) return result.choice() == null;
        return result.choice() != null && result.choice().timeShiftMinutes() == choice.path("timeShiftMinutes").asInt()
                && result.choice().venueChanged() == choice.path("venueChanged").asBoolean()
                && result.choice().replacedEquipmentLines() == choice.path("replacedEquipmentLines").asInt();
    }
    private static JsonNode read(Path file) throws Exception { return JSON.readTree(Files.readString(file).replace("${DEMO_DATE}", DATE)); }
    private static long count(String table) { return db.jdbc().queryForObject("SELECT count(*) FROM " + table, Long.class); }
    private static Instant instant(JsonNode node, String key) { return java.time.OffsetDateTime.parse(node.path(key).asText()).toInstant(); }
    private static UUID uuid(long tail) { return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", tail)); }
}
