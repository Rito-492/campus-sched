package com.campus.events.acceptance;

import com.campus.events.contract.EquipmentNeed;
import com.campus.events.contract.Identity;
import com.campus.events.contract.Plan;
import com.campus.events.scheduling.StrategyEvaluator;
import com.campus.events.scheduling.StrategyResult;
import com.campus.events.support.DbTestSupport;
import com.campus.events.support.TestClockConfiguration;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * QA-04 策略评测 Java 侧执行器（SchedulingBenchmarkIT），对应验收场景 D-AT-22。
 *
 * <p>职责：按 {@code docs/testing/test-data-plan.md §7} 装载评测场景 → 调用设计师（DS）的
 * {@link StrategyEvaluator} → 把每场景每策略的原始测量行写
 * {@code server/target/scheduling-evaluation.json}，供 {@code qa/scripts/evaluate-scheduling.ts}
 * 汇总为 {@code artifacts/evaluation/results.csv} 与 {@code summary.md}（03 §4）。本测试只产出原始
 * 测量行，不判定结论、不补齐缺失行、不填充占位数值。
 *
 * <p>装载规则（test-data-plan §7）：评测环境 = {@code demo.json} 的 organizations/users/resources
 * 参考数据 + {@code scenarios/scheduling.json} 场景数据；<b>不装载演示活动 A/B 与报名</b>，避免场景
 * 互相干扰。{@code blockers} 合成 SCHEDULED、versionNo=1 的占用活动及其 ACTIVE 预约行
 * （区间取 reservations 原值，quantity 按行）；{@code faults} 写 {@code resource_fault}；
 * {@code request.subjectTail} 为被评估活动，作为 {@link StrategyEvaluator#evaluateStrategies} 的
 * {@code excludeActivityId}（02 §8）。每个场景跑前重置场景数据（保留参考数据）。
 *
 * <p>计时与映射（02 §8）：每场景先预热 {@code warmupRuns}(=1) 次并丢弃，再测量
 * {@code measuredRuns}(=10) 次；{@code elapsedMs = elapsedNanos / 1_000_000.0}。结果严格含
 * FIRST_FIT 与 RANKED 两项；feasible=false 时 choice=null，三个代价字段写 null，不伪造 0 代价。
 *
 * <p>编译依赖（本文件唯一例外）：评测必须直接调用 DS 的 Java API，故 import
 * {@code com.campus.events.contract}（Plan/EquipmentNeed/Identity）与
 * {@code com.campus.events.scheduling}（StrategyEvaluator/StrategyResult）；其余断言仅用 Jackson JsonNode。
 *
 * <p>执行命令（在仓库根目录）：
 * {@code ./server/mvnw.cmd -f server/pom.xml "-Dit.test=SchedulingBenchmarkIT" verify}
 * 然后 {@code npm --prefix qa run evaluate} 汇总原始行。
 */
@SpringBootTest
@Import(TestClockConfiguration.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@DisplayName("QA-04 / D-AT-22 策略评测执行器（SchedulingBenchmarkIT）")
class SchedulingBenchmarkIT {

    private static final ObjectMapper MAPPER = new ObjectMapper();

    private static final String DEMO_DATE = "2030-10-20";
    private static final String TIMING_SCOPE = "候选生成、资源读取、约束检查及选择/排序（不含HTTP与文件输出）";
    private static final List<String> STRATEGIES = List.of("FIRST_FIT", "RANKED");
    private static final int EXPECTED_SCENARIOS = 8;

    private static final UUID ORG_1001 = uuid(1001);
    private static final UUID USER_APPLICANT_A = uuid(2001);
    private static final UUID USER_RESOURCE_ADMIN = uuid(2004);

    @RegisterExtension
    static final DbTestSupport db = DbTestSupport.create();

    @Autowired
    private StrategyEvaluator evaluator;

    /** 先装载参考数据（demo 的组织/用户/资源），场景数据在各测试方法内按场景装载。 */
    @BeforeAll
    static void loadReferenceData() {
        db.loadReferenceData("demo");
    }

    @Test
    @DisplayName("D-AT-22 / QA-04 八场景策略评测：FIRST_FIT 与 RANKED 各测量 10 次")
    void benchmarkAllScenarios() throws IOException {
        JsonNode scenarioFile = readScenarioFile();
        JsonNode scenarios = scenarioFile.path("scenarios");
        int warmupRuns = scenarioFile.path("evaluation").path("warmupRuns").asInt();
        int measuredRuns = scenarioFile.path("evaluation").path("measuredRuns").asInt();
        assertEquals(EXPECTED_SCENARIOS, scenarios.size(), "scheduling.json 应含 8 个评测场景");

        List<ObjectNode> runs = new ArrayList<>();
        Map<String, List<ObjectNode>> groups = new LinkedHashMap<>();

        for (JsonNode scenario : scenarios) {
            String scenarioId = scenario.path("scenarioId").asText();
            resetScenarioData();
            loadScenario(scenario);
            assertScenarioLoaded(scenario);

            Plan plan = toPlan(scenario.path("request").path("plan"));
            int attendees = scenario.path("request").path("expectedAttendees").asInt();
            UUID excludeActivityId = uuid(scenario.path("request").path("subjectTail").asLong());

            for (int warmup = 0; warmup < warmupRuns; warmup++) {
                evaluateStrategies(plan, attendees, excludeActivityId);
            }
            for (int runIndex = 1; runIndex <= measuredRuns; runIndex++) {
                for (StrategyResult result : evaluateStrategies(plan, attendees, excludeActivityId)) {
                    ObjectNode row = toRunNode(scenarioId, runIndex, result);
                    runs.add(row);
                    groups.computeIfAbsent(scenarioId + "\u0000" + result.strategy(), key -> new ArrayList<>())
                            .add(row);
                }
            }
        }

        assertScenarioCoverage(scenarios, measuredRuns, groups);

        Path out = resolveOutputFile();
        Files.createDirectories(out.getParent());
        MAPPER.writerWithDefaultPrettyPrinter().writeValue(out.toFile(), buildOutput(runs));

        JsonNode reread = MAPPER.readTree(out.toFile());
        assertEquals(EXPECTED_SCENARIOS * STRATEGIES.size() * measuredRuns, reread.path("runs").size(),
                "读回校验：runs 行数应等于 场景数×策略数×测量次数");
        List<String> knownIds = scenarioIds(scenarios);
        for (JsonNode row : reread.path("runs")) {
            assertTrue(knownIds.contains(row.path("scenarioId").asText()),
                    "读回结果的 scenarioId 必须与 scheduling.json 一致：" + row.path("scenarioId").asText());
        }
    }

    // ---------------------------------------------------------------- 评测调用

    private List<StrategyResult> evaluateStrategies(Plan plan, int attendees, UUID excludeActivityId) {
        List<StrategyResult> results =
                evaluator.evaluateStrategies(plan, attendees, excludeActivityId, Identity.TEACHER);
        assertEquals(STRATEGIES.size(), results.size(),
                "evaluateStrategies 必须严格返回 FIRST_FIT 与 RANKED 两项，实际 " + results.size());
        List<String> names = new ArrayList<>();
        for (StrategyResult result : results) {
            names.add(result.strategy());
        }
        assertTrue(names.containsAll(STRATEGIES), "结果必须含 FIRST_FIT 与 RANKED：" + names);
        return results;
    }

    private static ObjectNode toRunNode(String scenarioId, int runIndex, StrategyResult result) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("scenarioId", scenarioId);
        node.put("strategy", result.strategy());
        node.put("runIndex", runIndex);
        node.put("feasible", result.feasible());
        var choice = result.choice();
        if (choice == null) {
            node.putNull("timeShiftMinutes");
            node.putNull("venueChanged");
            node.putNull("replacedEquipmentLines");
        } else {
            node.put("timeShiftMinutes", choice.timeShiftMinutes());
            node.put("venueChanged", choice.venueChanged());
            node.put("replacedEquipmentLines", choice.replacedEquipmentLines());
        }
        node.put("evaluatedCount", result.evaluatedCount());
        node.put("elapsedMs", result.elapsedNanos() / 1_000_000.0);
        return node;
    }

    // ---------------------------------------------------------------- 场景装载

    /** 每场景独立：跑前清空上个场景的合成活动/预约/故障（TRUNCATE 不触发 append-only 行级 DELETE 触发器）。 */
    private static void resetScenarioData() {
        db.jdbc().execute("TRUNCATE TABLE activity, resource_fault CASCADE");
    }

    private static void loadScenario(JsonNode scenario) throws IOException {
        UUID fallbackVenue = uuid(scenario.path("request").path("plan").path("venueId").asText());
        for (JsonNode blocker : scenario.path("blockers")) {
            UUID activityId = uuid(blocker.path("tail").asLong());
            UUID venueId = resolveVenue(blocker, fallbackVenue);
            Instant start = null;
            Instant end = null;
            for (JsonNode reservation : blocker.path("reservations")) {
                Instant reservationStart = instant(reservation.path("startAt").asText());
                Instant reservationEnd = instant(reservation.path("endAt").asText());
                if (start == null || reservationStart.isBefore(start)) {
                    start = reservationStart;
                }
                if (end == null || reservationEnd.isAfter(end)) {
                    end = reservationEnd;
                }
            }
            String inputJson = blockerInputJson(blocker, venueId, start, end);
            db.jdbc().update("INSERT INTO activity (id, organization_id, owner_id, status, version_no, effective_version_no, lock_version, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    activityId, ORG_1001, USER_APPLICANT_A, "SCHEDULED", 1, 1, 0, ts(), ts());
            db.jdbc().update("INSERT INTO activity_version (activity_id, version_no, input_json, created_by, created_at) VALUES (?,?,?,?,?)",
                    activityId, 1, inputJson, USER_APPLICANT_A, ts());
            for (JsonNode reservation : blocker.path("reservations")) {
                db.jdbc().update("INSERT INTO reservation (id, activity_id, version_no, resource_id, start_at, end_at, quantity, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        UUID.randomUUID(), activityId, 1,
                        uuid(reservation.path("resourceId").asText()),
                        Timestamp.from(instant(reservation.path("startAt").asText())),
                        Timestamp.from(instant(reservation.path("endAt").asText())),
                        reservation.path("quantity").asInt(), "ACTIVE", ts(), ts());
            }
        }
        for (JsonNode fault : scenario.path("faults")) {
            db.jdbc().update("INSERT INTO resource_fault (id, resource_id, start_at, end_at, unavailable_quantity, reason, created_by, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    UUID.randomUUID(), uuid(fault.path("resourceId").asText()),
                    Timestamp.from(instant(fault.path("startAt").asText())),
                    Timestamp.from(instant(fault.path("endAt").asText())),
                    fault.path("unavailableQuantity").asInt(), fault.path("reason").asText(),
                    USER_RESOURCE_ADMIN, ts(), ts());
        }
    }

    /** blockers 场地：取 blocker 的场地（kind=VENUE）预约行，无场地行时取 request.plan.venueId（test-data-plan §7）。 */
    private static UUID resolveVenue(JsonNode blocker, UUID fallbackVenue) {
        for (JsonNode reservation : blocker.path("reservations")) {
            UUID resourceId = uuid(reservation.path("resourceId").asText());
            if ("VENUE".equals(resourceKind(resourceId))) {
                return resourceId;
            }
        }
        return fallbackVenue;
    }

    private static String resourceKind(UUID resourceId) {
        return db.jdbc().queryForObject("SELECT kind FROM resource WHERE id = ?", String.class, resourceId);
    }

    /** 合成占用活动 input_json：title=blocker.title，expectedAttendees=registrationCapacity=1，staffUserIds=[]，setup/teardown=0。 */
    private static String blockerInputJson(JsonNode blocker, UUID venueId, Instant start, Instant end) throws IOException {
        ObjectNode input = MAPPER.createObjectNode();
        input.put("title", blocker.path("title").asText());
        input.put("description", blocker.path("title").asText() + "（评测场景占用，测试数据）");
        input.put("expectedAttendees", 1);
        input.put("registrationCapacity", 1);
        input.putArray("staffUserIds");
        ObjectNode plan = input.putObject("plan");
        plan.put("startAt", start.toString());
        plan.put("endAt", end.toString());
        plan.put("setupMinutes", 0);
        plan.put("teardownMinutes", 0);
        plan.put("venueId", venueId.toString());
        plan.putArray("equipment");
        return MAPPER.writeValueAsString(input);
    }

    private static void assertScenarioLoaded(JsonNode scenario) {
        int blockers = scenario.path("blockers").size();
        int faults = scenario.path("faults").size();
        assertEquals((long) blockers, count("SELECT count(*) FROM activity"),
                "评测环境只应含本场景合成占用活动（不得装载演示活动 A/B）");
        long reservations = 0;
        for (JsonNode blocker : scenario.path("blockers")) {
            reservations += blocker.path("reservations").size();
        }
        assertEquals(reservations, count("SELECT count(*) FROM reservation WHERE status = 'ACTIVE'"),
                "占用活动的 ACTIVE 预约行数应与 blockers.reservations 一致");
        assertEquals((long) faults, count("SELECT count(*) FROM resource_fault"),
                "resource_fault 行数应与 faults 一致");
    }

    // ---------------------------------------------------------------- 断言

    private static void assertScenarioCoverage(JsonNode scenarios, int measuredRuns,
                                               Map<String, List<ObjectNode>> groups) {
        for (JsonNode scenario : scenarios) {
            String scenarioId = scenario.path("scenarioId").asText();
            for (String strategy : STRATEGIES) {
                List<ObjectNode> rows = groups.getOrDefault(scenarioId + "\u0000" + strategy, List.of());
                assertEquals(measuredRuns, rows.size(),
                        scenarioId + "/" + strategy + " 测量行数应恰为 " + measuredRuns);
                List<Integer> seen = new ArrayList<>();
                String signature = null;
                for (ObjectNode row : rows) {
                    int runIndex = row.path("runIndex").asInt();
                    assertFalse(seen.contains(runIndex), scenarioId + "/" + strategy + " runIndex 重复：" + runIndex);
                    seen.add(runIndex);
                    String current = row.path("feasible").asText() + "/"
                            + row.path("timeShiftMinutes").asText() + "/"
                            + row.path("venueChanged").asText() + "/"
                            + row.path("replacedEquipmentLines").asText() + "/"
                            + row.path("evaluatedCount").asText();
                    if (signature == null) {
                        signature = current;
                    } else {
                        assertEquals(signature, current,
                                scenarioId + "/" + strategy + " 多次测量结果应一致（策略确定性）");
                    }
                }
            }
        }
    }

    // ---------------------------------------------------------------- 输出

    private static ObjectNode buildOutput(List<ObjectNode> runs) {
        ObjectNode output = MAPPER.createObjectNode();
        output.put("schemaVersion", 1);
        output.put("generatedAt", Instant.now().toString());
        output.set("environment", buildEnvironment());
        output.put("timingScope", TIMING_SCOPE);
        ArrayNode array = output.putArray("runs");
        for (ObjectNode run : runs) {
            array.add(run);
        }
        return output;
    }

    private static ObjectNode buildEnvironment() {
        String machine = System.getenv("MACHINE");
        if (machine == null || machine.isBlank()) {
            machine = System.getProperty("os.name", "unknown") + "/" + System.getProperty("os.arch", "unknown");
        }
        String commit = System.getenv("GIT_COMMIT");
        ObjectNode environment = MAPPER.createObjectNode();
        environment.put("machine", machine);
        environment.put("os", System.getProperty("os.name", "unknown")
                + " " + System.getProperty("os.version", "")
                + " (" + System.getProperty("os.arch", "unknown") + ")");
        environment.put("jdk", System.getProperty("java.version", "unknown")
                + " (" + System.getProperty("java.vendor", "unknown") + ")");
        environment.put("commit", commit == null || commit.isBlank() ? "未提供（构建时注入）" : commit);
        environment.put("demoDate", DEMO_DATE);
        environment.put("clock", "2030-10-19T00:00:00Z");
        return environment;
    }

    // ---------------------------------------------------------------- 路径与工具

    private static JsonNode readScenarioFile() throws IOException {
        Path file = resolveScenarioFile();
        String raw = Files.readString(file).replace("${DEMO_DATE}", DEMO_DATE);
        return MAPPER.readTree(raw);
    }

    private static Path resolveScenarioFile() {
        String dir = System.getProperty("test.data.dir", "");
        List<Path> candidates = new ArrayList<>();
        if (!dir.isEmpty()) {
            candidates.add(Path.of(dir, "scenarios", "scheduling.json"));
        }
        candidates.add(Path.of("test-data", "scenarios", "scheduling.json"));
        candidates.add(Path.of("..", "test-data", "scenarios", "scheduling.json"));
        for (Path candidate : candidates) {
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到评测场景文件 scenarios/scheduling.json，已查找 " + candidates);
    }

    private static Path resolveOutputFile() {
        String dir = System.getProperty("test.output.dir", "");
        List<Path> candidates = new ArrayList<>();
        if (!dir.isEmpty()) {
            candidates.add(Path.of(dir, "scheduling-evaluation.json"));
        }
        candidates.add(Path.of("target", "scheduling-evaluation.json"));
        candidates.add(Path.of("server", "target", "scheduling-evaluation.json"));
        for (Path candidate : candidates) {
            Path parent = candidate.toAbsolutePath().getParent();
            if (parent != null && Files.isDirectory(parent)) {
                return candidate;
            }
        }
        return candidates.get(candidates.size() - 1);
    }

    private static Plan toPlan(JsonNode planNode) {
        List<EquipmentNeed> equipment = new ArrayList<>();
        for (JsonNode need : planNode.path("equipment")) {
            equipment.add(new EquipmentNeed(uuid(need.path("resourceId").asText()), need.path("quantity").asInt()));
        }
        return new Plan(
                instant(planNode.path("startAt").asText()),
                instant(planNode.path("endAt").asText()),
                planNode.path("setupMinutes").asInt(),
                planNode.path("teardownMinutes").asInt(),
                uuid(planNode.path("venueId").asText()),
                equipment);
    }

    private static List<String> scenarioIds(JsonNode scenarios) {
        List<String> ids = new ArrayList<>();
        for (JsonNode scenario : scenarios) {
            ids.add(scenario.path("scenarioId").asText());
        }
        return ids;
    }

    private static long count(String sql) {
        Long value = db.jdbc().queryForObject(sql, Long.class);
        return value == null ? 0L : value;
    }

    private static Instant instant(String text) {
        return OffsetDateTime.parse(text).toInstant();
    }

    private static Timestamp ts() {
        return Timestamp.from(DbTestSupport.FIXED_INSTANT);
    }

    private static UUID uuid(String text) {
        return UUID.fromString(text);
    }

    private static UUID uuid(long tail) {
        return UUID.fromString(String.format("00000000-0000-0000-0000-%012d", tail));
    }
}
