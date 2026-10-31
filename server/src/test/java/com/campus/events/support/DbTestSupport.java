package com.campus.events.support;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.extension.AfterAllCallback;
import org.junit.jupiter.api.extension.BeforeAllCallback;
import org.junit.jupiter.api.extension.ExtensionContext;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import javax.sql.DataSource;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
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
 * <p>v3.0 表结构依据：账号授权按 02 契约 §4 拆分写入 app_user（含 identity）、user_role、
 * user_permission、user_approval_scope、user_resource_scope；资源写入 resource 的
 * allow_teacher/allow_student。数据文件错误在装载时自检并抛出 IllegalStateException。
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
    private TransactionTemplate transactions;

    private DbTestSupport(List<String> dataFiles) {
        this.dataFiles = dataFiles;
        this.schemaName = "t_" + UUID.randomUUID().toString().replace("-", "");
        if (!SCHEMA_PATTERN.matcher(schemaName).matches()) {
            throw new IllegalStateException("schema 名不符合 t_[0-9a-f]{32}：" + schemaName);
        }
        // 提前发布，供 Spring 测试配置的 DataSource 在上下文创建时读取
        System.setProperty("test.schema.name", schemaName);
        configureSpringDataSource(schemaName);
    }

    /**
     * 为使用 @SpringBootTest 的验收用例启用test profile，并绑定本类独立schema。
     * application-test.yml提供TEST_DB_*凭据；这里仅写入不含密码的URL/schema系统属性。
     */
    private static void configureSpringDataSource(String schemaName) {
        String url = requireEnv("TEST_DATABASE_URL");
        if (!url.contains("campus_test")) {
            throw new IllegalStateException("TEST_DATABASE_URL 必须指向 campus_test（禁止使用开发库/外部库）");
        }
        String scopedUrl = url + (url.contains("?") ? "&" : "?") + "currentSchema=" + schemaName;
        System.setProperty("spring.profiles.active", "test");
        System.setProperty("spring.datasource.url", scopedUrl);
        System.setProperty("spring.flyway.schemas", schemaName);
        System.setProperty("spring.flyway.default-schema", schemaName);
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
        String user = requireEnvAny("TEST_DB_USERNAME", "TEST_DATABASE_USERNAME");
        String password = requireEnvAny("TEST_DB_PASSWORD", "TEST_DATABASE_PASSWORD");
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
        this.transactions = new TransactionTemplate(new DataSourceTransactionManager(dataSource));

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
        String user = requireEnvAny("TEST_DB_USERNAME", "TEST_DATABASE_USERNAME");
        String password = requireEnvAny("TEST_DB_PASSWORD", "TEST_DATABASE_PASSWORD");
        // 只清理自己创建的 schema；名称已校验，无注入面
        DataSource admin = new DriverManagerDataSource(url, user, password);
        new JdbcTemplate(admin).execute("DROP SCHEMA IF EXISTS \"" + schemaName + "\" CASCADE");
    }

    // ---------------------------------------------------------------- 装载

    /**
     * 装载 test-data/ 下的 JSON 数据文件（v3.0 表结构依据 02 §4；派生规则见 docs/testing/test-data-plan.md §5）。
     * 账号授权拆分为 app_user/user_role/user_permission/user_approval_scope/user_resource_scope 写入，
     * 资源含 allow_teacher/allow_student。可在单个测试方法内调用，例如装载并发报名场景数据。
     */
    public void loadTestData(String dataFile) {
        try {
            JsonNode root = readDataFile(dataFile);
            String passwordHash = ENCODER.encode(requireEnv("DEMO_PASSWORD"));
            transactions.executeWithoutResult(status -> {
                insertReferenceTables(root, passwordHash);
                try {
                    insertActivities(root);
                } catch (JsonProcessingException e) {
                    throw new IllegalStateException("序列化活动 input 失败", e);
                }
                insertRegistrations(root);
            });
        } catch (Exception e) {
            throw new IllegalStateException("装载测试数据失败：" + dataFile, e);
        }
    }

    /**
     * 仅装载组织/用户/资源（参考数据）；评测场景不装载演示活动与报名（test-data-plan §7）。
     *
     * <p>用于 SchedulingBenchmarkIT：评测环境 = {@code demo.json} 的 organizations/users/resources
     * + {@code scenarios/scheduling.json} 场景数据；不装载演示活动 A/B 与报名，避免场景互相干扰。
     * 只写 organization/app_user/user_role/user_permission/user_approval_scope/user_resource_scope/resource 表。
     */
    public void loadReferenceData(String dataFile) {
        try {
            JsonNode root = readDataFile(dataFile);
            String passwordHash = ENCODER.encode(requireEnv("DEMO_PASSWORD"));
            transactions.executeWithoutResult(status -> insertReferenceTables(root, passwordHash));
        } catch (Exception e) {
            throw new IllegalStateException("装载参考数据失败：" + dataFile, e);
        }
    }

    /** 解析数据文件并替换 ${DEMO_DATE} 占位符（03 §1 固定演示日期）。 */
    private JsonNode readDataFile(String dataFile) throws IOException {
        Path file = resolveDataFile(dataFile);
        String raw = Files.readString(file).replace("${DEMO_DATE}", DEMO_DATE.toString());
        return MAPPER.readTree(raw);
    }

    /** organization/app_user/user_role/user_permission/user_approval_scope/user_resource_scope/resource 参考数据。 */
    private void insertReferenceTables(JsonNode root, String passwordHash) {
        List<ManagedResourceScope> managedResourceScopes = new ArrayList<>();

        JsonNode organizations = root.path("organizations");
        for (JsonNode n : organizations) {
            jdbc.update("INSERT INTO organization (id, name, created_at, updated_at) VALUES (?,?,?,?)",
                    uuid(n.get("id")), n.get("name").asText(), ts(), ts());
        }

        JsonNode users = root.path("users");
        for (JsonNode n : users) {
            UUID userId = uuid(n.get("id"));
            String username = n.get("username").asText();
            String identity = n.get("identity").asText();
            if (!"TEACHER".equals(identity) && !"STUDENT".equals(identity)) {
                throw new IllegalStateException("用户 " + username
                        + " 的 identity 必须为 TEACHER/STUDENT，实际为：" + identity);
            }
            List<String> roles = textValues(n.path("roles"));
            JsonNode permissions = n.path("permissions");
            JsonNode approvalScopes = n.path("approvalScopes");
            JsonNode managedResourceIds = n.path("managedResourceIds");
            if (permissions.size() > 0 && !roles.contains("ADMIN")) {
                throw new IllegalStateException("用户 " + username
                        + " 配置了 permissions 但 roles 不含 ADMIN（02 三角色授权规则）");
            }
            if (approvalScopes.size() > 0 && !roles.contains("APPROVER")) {
                throw new IllegalStateException("用户 " + username
                        + " 配置了 approvalScopes 但 roles 不含 APPROVER（02 三角色授权规则）");
            }
            jdbc.update("INSERT INTO app_user (id, organization_id, username, display_name, password_hash, enabled, identity, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    userId, uuid(n.get("organizationId")), username,
                    n.get("displayName").asText(), passwordHash, true, identity, ts(), ts());
            for (String role : roles) {
                jdbc.update("INSERT INTO user_role (user_id, role, created_at) VALUES (?,?,?)",
                        userId, role, ts());
            }
            for (JsonNode permission : permissions) {
                jdbc.update("INSERT INTO user_permission (user_id, permission, created_at) VALUES (?,?,?)",
                        userId, permission.asText(), ts());
            }
            for (JsonNode scope : approvalScopes) {
                UUID scopeOrganizationId = uuid(scope.get("organizationId"));
                for (JsonNode category : scope.path("resourceCategories")) {
                    jdbc.update("INSERT INTO user_approval_scope (user_id, organization_id, resource_category, created_at) VALUES (?,?,?,?)",
                            userId, scopeOrganizationId, category.asText(), ts());
                }
            }
            for (JsonNode resourceId : managedResourceIds) {
                managedResourceScopes.add(new ManagedResourceScope(userId, uuid(resourceId)));
            }
        }

        JsonNode resources = root.path("resources");
        for (JsonNode n : resources) {
            jdbc.update("INSERT INTO resource (id, kind, name, category, capacity, quantity, open_from, open_to, allow_teacher, allow_student, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?,?,?,?)",
                    uuid(n.get("id")), n.get("kind").asText(), n.get("name").asText(),
                    n.get("category").asText(),
                    n.has("capacity") && !n.get("capacity").isNull() ? n.get("capacity").asInt() : null,
                    n.get("quantity").asInt(), LocalTime.parse(n.get("openFrom").asText()),
                    LocalTime.parse(n.get("openTo").asText()),
                    n.get("allowTeacher").asBoolean(), n.get("allowStudent").asBoolean(),
                    ts(), ts());
        }

        // V1 对 resource_id 使用即时外键；范围授权必须在资源实际插入后写入。
        for (ManagedResourceScope scope : managedResourceScopes) {
            jdbc.update("INSERT INTO user_resource_scope (user_id, resource_id, created_at) VALUES (?,?,?)",
                    scope.userId(), scope.resourceId(), ts());
        }
    }

    /** 演示活动及其版本、预约（仅装载测试数据时写入；评测参考数据不写入）。 */
    private void insertActivities(JsonNode root) throws JsonProcessingException {
        JsonNode activities = root.path("activities");
        for (JsonNode n : activities) {
            String status = n.get("status").asText();
            int versionNo = n.get("versionNo").asInt();
            boolean effective = "SCHEDULED".equals(status) || "OPEN".equals(status);
            jdbc.update("INSERT INTO activity (id, organization_id, owner_id, status, version_no, effective_version_no, lock_version, created_at, updated_at) VALUES (?,?,?,?,?,?,?,?,?)",
                    uuid(n.get("id")), uuid(n.get("organizationId")), uuid(n.get("ownerId")),
                    status, versionNo, effective ? versionNo : null, 0, ts(), ts());
            jdbc.update("INSERT INTO activity_version (activity_id, version_no, input_json, created_by, created_at) VALUES (?,?,?::jsonb,?,?)",
                    uuid(n.get("id")), versionNo, MAPPER.writeValueAsString(n.get("input")),
                    uuid(n.get("ownerId")), ts());
            if (effective) {
                insertReservations(uuid(n.get("id")), versionNo, n.get("input").get("plan"));
            }
        }
    }

    /** 报名记录（仅装载测试数据时写入；评测参考数据不写入）。 */
    private void insertRegistrations(JsonNode root) {
        JsonNode registrations = root.path("registrations");
        for (JsonNode n : registrations) {
            jdbc.update("INSERT INTO activity_registration (activity_id, user_id, status, version_no, created_at) VALUES (?,?,?,?,?)",
                    uuid(n.get("activityId")), uuid(n.get("userId")),
                    n.get("status").asText(), n.get("versionNo").asInt(), ts());
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

    /** 资源资格只读视图（02 §2 ResourceEligibility 的测试侧对应）。 */
    public record ResourceEligibility(boolean allowTeacher, boolean allowStudent) {
    }

    /** 单条审批范围授权（02 §4 user_approval_scope 一行的测试侧对应）。 */
    public record ApprovalScopeEntry(UUID organizationId, String resourceCategory) {
    }

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

    /** 账号身份（身份资格断言：TEACHER/STUDENT，02 §4 app_user.identity）。 */
    public String identityOf(UUID userId) {
        return jdbc.queryForObject("SELECT identity FROM app_user WHERE id = ?", String.class, userId);
    }

    /** 资源资格（D-AT 资格断言：allow_teacher/allow_student，02 §4 resource）。 */
    public ResourceEligibility eligibilityOf(UUID resourceId) {
        return jdbc.queryForObject(
                "SELECT allow_teacher, allow_student FROM resource WHERE id = ?",
                (rs, rowNum) -> new ResourceEligibility(
                        rs.getBoolean("allow_teacher"), rs.getBoolean("allow_student")),
                resourceId);
    }

    /** 账号资源管理范围条数（授权范围断言：02 §4 user_resource_scope）。 */
    public long managedResourceCount(UUID userId) {
        Long count = jdbc.queryForObject(
                "SELECT count(*) FROM user_resource_scope WHERE user_id = ?", Long.class, userId);
        return count == null ? 0 : count;
    }

    /** 账号审批范围（授权范围断言：02 §4 user_approval_scope，按组织与类别排序）。 */
    public List<ApprovalScopeEntry> approvalScopeOf(UUID userId) {
        return jdbc.query(
                "SELECT organization_id, resource_category FROM user_approval_scope WHERE user_id = ? ORDER BY organization_id, resource_category",
                (rs, rowNum) -> new ApprovalScopeEntry(
                        rs.getObject("organization_id", UUID.class), rs.getString("resource_category")),
                userId);
    }

    // ---------------------------------------------------------------- 内部

    private record ManagedResourceScope(UUID userId, UUID resourceId) {
    }

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

    /** 环境变量取名分歧的兼容读取：以 CM 配置的 TEST_DB_* 为准，回退 TEST_DATABASE_*（见 handoff 裁决记录）。 */
    private static String requireEnvAny(String primary, String fallback) {
        String value = System.getenv(primary);
        if (value != null && !value.isBlank()) {
            return value;
        }
        return requireEnv(fallback);
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

    private static List<String> textValues(JsonNode array) {
        List<String> values = new ArrayList<>();
        for (JsonNode node : array) {
            values.add(node.asText());
        }
        return values;
    }

    private static Timestamp ts() {
        return Timestamp.from(FIXED_INSTANT);
    }
}
