package com.campus.events.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import javax.sql.DataSource;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * 每测试类独立 schema 的数据库测试支撑（QA-01，03 契约 §3）。
 *
 * <p>用法（JUnit 5）：
 * <pre>{@code
 * @RegisterExtension
 * static final DbTestSupport db = DbTestSupport.create("demo");
 *
 * @Test
 * void check() {
 *     JdbcTemplate jdbc = db.jdbc();
 *     db.loadDemo("scenarios/registration-concurrency");
 * }
 * }</pre>
 *
 * <p>约束（03 §3）：数据库固定 campus_test；schema 名严格匹配 {@code t_[0-9a-f]{32}}；
 * Flyway 迁移后载入独立数据；结束只清理自己创建的 schema；禁止对 campus_dev 或外部库清表。
 *
 * <p>注意：schema 名与 {@code test.schema.name} 系统属性在 {@link #create} 时即生成并设置，
 * 早于 Spring 测试上下文加载；迁移与数据装载在 beforeAll 完成，首个查询前一切就绪。
 * 使用 @SpringBootTest 的集成测试请为每个类加 {@code @DirtiesContext(classMode = AFTER_CLASS)}，
 * 避免上下文缓存把上一类的 schema 配置复用到下一类。
 */
public final class DbTestSupport implements BeforeAllCallback, AfterAllCallback {

    /** schema 命名规则：t_ + 32 位小写十六进制（UUID 去连字符）。 */
    private static final Pattern SCHEMA_PATTERN = Pattern.compile("^t_[0-9a-f]{32}$");

    /** 固定测试当前时间（03 §1：Clock=2030-10-19T00:00:00Z）。 */
    public static final Instant FIXED_INSTANT = Instant.parse("2030-10-19T00:00:00Z");

    /** 固定演示日期 D（03 §1：D=2030-10-20，测试不得依赖实际当天）。 */
    public static final LocalDate DEMO_DATE = LocalDate.of(2030, 10, 20);

    private static final ObjectMapper MAPPER = new ObjectMapper();
    private static final BCryptPasswordEncoder ENCODER = new BCryptPasswordEncoder();

    private final List<String> dataFiles;
    private final String schemaName;
    private DataSource dataSource;
    private JdbcTemplate jdbc;

    private DbTestSupport(List<String> dataFiles) {
        this.dataFiles = dataFiles;
        this.schemaName = "t_" + UUID.randomUUID().toString().replace("-", "");
        if (!SCHEMA_PATTERN.matcher(schemaName).matches()) {
            throw new IllegalStateException("schema 名不符合 t_[0-9a-f]{32}：" + schemaName);
        }
        // 提前发布，供 Spring 测试配置的 DataSource 在上下文创建时读取
        System.setProperty("test.schema.name", schemaName);
    }

    /** 创建支撑实例；参数为 test-data/ 下的数据文件（省略 .json 后缀），如 "demo"、"scenarios/registration-concurrency"。 */
    public static DbTestSupport create(String... dataFiles) {
        return new DbTestSupport(List.of(dataFiles));
    }

    /** 当前测试类的 schema 名。 */
    public String schemaName() {
        return schemaName;
    }

    /** 绑定到本 schema 的 DataSource。 */
    public DataSource dataSource() {
        return dataSource;
    }

    /** 绑定到本 schema 的 JdbcTemplate。 */
    public JdbcTemplate jdbc() {
        return jdbc;
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        String url = requireEnv("TEST_DATABASE_URL");
        String user = requireEnv("TEST_DATABASE_USERNAME");
        String password = requireEnv("TEST_DATABASE_PASSWORD");
        if (!url.contains("campus_test")) {
            throw new IllegalStateException(
                    "TEST_DATABASE_URL 必须指向 campus_test，当前为：" + url + "（禁止对 campus_dev 或外部库操作）");
        }
        String scopedUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schemaName;

        DataSource admin = new DriverManagerDataSource(url, user, password);
        new JdbcTemplate(admin).execute("CREATE SCHEMA \"" + schemaName + "\"");

        Flyway.configure()
                .dataSource(admin)
                .schemas(schemaName)
                .defaultSchema(schemaName)
                .locations("classpath:db/migration")
                .load()
                .migrate();

        this.dataSource = new DriverManagerDataSource(scopedUrl, user, password);
        this.jdbc = new JdbcTemplate(dataSource);

        for (String file : dataFiles) {
            loadTestData(file);
        }
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (dataSource == null) {
            return;
        }
        String url = requireEnv("TEST_DATABASE_URL");
        String user = requireEnv("TEST_DATABASE_USERNAME");
        String password = requireEnv("TEST_DATABASE_PASSWORD");
        // 只清理自己创建的 schema；名称已校验，无注入面
        DataSource admin = new DriverManagerDataSource(url, user, password);
        new JdbcTemplate(admin).execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
    }

    // ---------------------------------------------------------------- 装载

    /**
     * 装载 test-data/ 下的 JSON 数据文件（派生规则见 docs/testing/test-data-plan.md §5）。
     * 可在单个测试方法内调用，例如装载并发报名场景数据。
     */
    public void loadTestData(String dataFile) {
        try {
            Path file = resolveDataFile(dataFile);
            String raw = Files.readString(file).replace("${DEMO_DATE}", DEMO_DATE.toString());
            JsonNode root = MAPPER.readTree(raw);
            String demoPassword = requireEnv("DEMO_PASSWORD");
            String passwordHash = ENCODER.encode(demoPassword);

            JsonNode organizations = root.path("organizations");
            for (JsonNode n : organizations) {
                jdbc.update("INSERT INTO organization (id, name, created_at, updated_at) VALUES (?,?,?,?)",
                        uuid(n.get("id")), n.get("name").asText(), ts(), ts());
            }

            JsonNode users = root.path("users");
            for (JsonNode n : users) {
                jdbc.update("INSERT INTO app_user (id, organization_id, username, display_name, password_hash, enabled, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?)",
                        uuid(n.get("id")), uuid(n.get("organizationId")), n.get("username").asText(),
                        n.get("displayName").asText(), passwordHash, true, ts(), ts());
                for (JsonNode role : n.get("roles")) {
                    jdbc.update("INSERT INTO user_role (user_id, role, created_at) VALUES (?,?,?)",
                            uuid(n.get("id")), role.asText(), ts());
                }
            }

            JsonNode resources = root.path("resources");
            for (JsonNode n : resources) {
                jdbc.update("INSERT INTO resource (id, kind, name, category, capacity, quantity, open_from, open_to, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                        uuid(n.get("id")), n.get("kind").asText(), n.get("name").asText(),
                        n.get("category").asText(),
                        n.has("capacity") && !n.get("capacity").isNull() ? n.get("capacity").asInt() : null,
                        n.get("quantity").asInt(), n.get("openFrom").asText(), n.get("openTo").asText(),
                        ts(), ts());
            }

            JsonNode activities = root.path("activities");
            for (JsonNode n : activities) {
                String status = n.get("status").asText();
                int versionNo = n.get("versionNo").asInt();
                boolean effective = "SCHEDULED".equals(status) || "OPEN".equals(status);
                jdbc.update("INSERT INTO activity (id, organization_id, owner_id, status, version_no, effective_version_no, lock_version, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                        uuid(n.get("id")), uuid(n.get("organizationId")), uuid(n.get("ownerId")),
                        status, versionNo, effective ? versionNo : null, 0, ts(), ts());
                jdbc.update("INSERT INTO activity_version (activity_id, version_no, input_json, created_by, created_at) VALUES (?,?,?,?,?)",
                        uuid(n.get("id")), versionNo, MAPPER.writeValueAsString(n.get("input")),
                        uuid(n.get("ownerId")), ts());
                if (effective) {
                    insertReservations(uuid(n.get("id")), versionNo, n.get("input").get("plan"));
                }
            }

            JsonNode registrations = root.path("registrations");
            for (JsonNode n : registrations) {
                jdbc.update("INSERT INTO activity_registration (activity_id, user_id, status, version_no, created_at) VALUES (?,?,?,?,?)",
                        uuid(n.get("activityId")), uuid(n.get("userId")),
                        n.get("status").asText(), n.get("versionNo").asInt(), ts());
            }
        } catch (Exception e) {
            throw new IllegalStateException("装载测试数据失败：" + dataFile, e);
        }
    }

    /** reservation 派生：区间 [startAt−setupMinutes, endAt+teardownMinutes)，场地 1 行 qty=1，器材逐条一行（01 基线 §4）。 */
    private void insertReservations(UUID activityId, int versionNo, JsonNode plan) {
        Instant start = OffsetDateTime.parse(plan.get("startAt").asText()).toInstant()
                .minusSeconds(plan.get("setupMinutes").asLong() * 60);
        Instant end = OffsetDateTime.parse(plan.get("endAt").asText()).toInstant()
                .plusSeconds(plan.get("teardownMinutes").asLong() * 60);
        insertReservation(activityId, versionNo, uuid(plan.get("venueId")), start, end, 1);
        for (JsonNode need : plan.path("equipment")) {
            insertReservation(activityId, versionNo, uuid(need.get("resourceId")), start, end,
                    need.get("quantity").asInt());
        }
    }

    private void insertReservation(UUID activityId, int versionNo, UUID resourceId,
                                   Instant start, Instant end, int quantity) {
        jdbc.update("INSERT INTO reservation (id, activity_id, version_no, resource_id, start_at, end_at, quantity, status, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?)",
                UUID.randomUUID(), activityId, versionNo, resourceId,
                Timestamp.from(start), Timestamp.from(end), quantity, "ACTIVE", ts(), ts());
    }

    // ---------------------------------------------------------------- 辅助查询
    // 03 §2 关键断言示例所需；其余断言辅助查询按用例需要追加

    /** 区间重叠的 ACTIVE 预约行数（03 §2：activeReservationsForVenueAndInterval 语义）。 */
    public long activeReservationsForVenueAndInterval(UUID venueId, Instant from, Instant to) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM reservation WHERE resource_id = ? AND status = 'ACTIVE' AND start_at < ? AND end_at > ?",
                Long.class, venueId, Timestamp.from(to), Timestamp.from(from));
        return count == null ? 0 : count;
    }

    /** 某活动当前 ACTIVE 预约行数（回滚/取消断言用）。 */
    public long activeReservationCount(UUID activityId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM reservation WHERE activity_id = ? AND status = 'ACTIVE'",
                Long.class, activityId);
        return count == null ? 0 : count;
    }

    /** 区间内 ACTIVE 预约数量合计（器材数量断言用）。 */
    public long sumActiveQuantity(UUID resourceId, Instant from, Instant to) {
        Long sum = jdbc.queryForObject(
                "SELECT COALESCE(sum(quantity),0) FROM reservation WHERE resource_id = ? AND status = 'ACTIVE' AND start_at < ? AND end_at > ?",
                Long.class, resourceId, Timestamp.from(to), Timestamp.from(from));
        return sum == null ? 0 : sum;
    }

    /** outbox 事件总数（回滚断言：事务前后应一致，03 §2）。 */
    public long outboxCount() {
        Long count = jdbc.queryForObject("SELECT count(*) FROM outbox_event", Long.class);
        return count == null ? 0 : count;
    }

    /** 活动当前有效版本号（回滚断言：事务前后应一致）。 */
    public Integer effectiveVersionNo(UUID activityId) {
        return jdbc.queryForObject(
                "SELECT effective_version_no FROM activity WHERE id = ?", Integer.class, activityId);
    }

    /** 活动状态（状态机断言）。 */
    public String activityStatus(UUID activityId) {
        return jdbc.queryForObject("SELECT status FROM activity WHERE id = ?", String.class, activityId);
    }

    /** 某收件人的通知条数（通知一致性断言：每收件人恰 1 条）。 */
    public long notificationCount(UUID recipientId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM notification WHERE recipient_id = ?", Long.class, recipientId);
        return count == null ? 0 : count;
    }

    // ---------------------------------------------------------------- 内部

    private static Path resolveDataFile(String dataFile) {
        String dir = System.getProperty("test.data.dir", "");
        List<Path> candidates = new ArrayList<>();
        if (!dir.isEmpty()) {
            candidates.add(Path.of(dir, dataFile + ".json"));
        }
        candidates.add(Path.of("test-data", dataFile + ".json"));
        candidates.add(Path.of("..", "test-data", dataFile + ".json"));
        for (Path p : candidates) {
            if (Files.isRegularFile(p)) {
                return p;
            }
        }
        throw new IllegalStateException("找不到测试数据文件：" + dataFile + ".json，已查找 " + candidates);
    }

    private static String requireEnv(String name) {
        String value = System.getenv(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 " + name + "（先执行 scripts/load-env.ps1，03 §3）");
        }
        return value;
    }

    private static UUID uuid(JsonNode node) {
        return UUID.fromString(node.asText());
    }

    private static Timestamp ts() {
        return Timestamp.from(FIXED_INSTANT);
    }
}
