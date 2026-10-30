package com.campus.events.dev;

import com.campus.events.support.DbTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * CM-01 演示数据种子导入器回归（真实 PostgreSQL，Failsafe 运行 *IT）。
 *
 * <p>覆盖 PR #8 评审要求的三条行为（幂等约定见 docs/testing/test-data-plan.md §5.6）：
 *   1. 首次导入：resource_admin 获得 12 条资源范围授权；
 *   2. 撤销一条后再次导入：撤销保留，不被重放（已有用户整体跳过，不修补其权限）；
 *   3. 删除用户后再次导入：新用户仍获得其配置范围。
 *
 * <p>另间接验证导入顺序：user_resource_scope 对 resource 有即时外键（V1__schema.sql §11），
 * 首次导入成功本身即证明「资源先于范围授权创建」。
 *
 * <p>装配方式：本类不启动完整 Spring 上下文，直接构造 DemoDataSeeder 并通过反射注入
 * {@code demoSeedFile}/{@code demoDate}/{@code demoPassword} 三个 @Value 字段，事务用
 * DataSourceTransactionManager 绑定到 DbTestSupport 的独立 schema。
 *
 * <p>运行前置：{@code TEST_DATABASE_URL} 指向 campus_test、{@code DEMO_PASSWORD} 已设置（先执行 scripts/load-env.ps1）。
 * 运行：{@code ./server/mvnw.cmd -f server/pom.xml "-Dit.test=DemoDataSeederIT" verify}
 */
@DisplayName("CM-01 DemoDataSeeder 导入顺序与幂等回归")
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class DemoDataSeederIT {

    @RegisterExtension
    static final DbTestSupport db = DbTestSupport.create();

    private static final UUID RESOURCE_ADMIN =
            UUID.fromString("00000000-0000-0000-0000-000000002004");

    @Test
    @Order(1)
    @DisplayName("首次导入：resource_admin 获得 12 条资源范围授权")
    void firstImportGrantsAllScopes() throws Exception {
        seed();
        assertEquals(12L, db.managedResourceCount(RESOURCE_ADMIN),
                "首次导入后 resource_admin 应有 12 条资源范围授权");
    }

    @Test
    @Order(2)
    @DisplayName("撤销一条后再次导入：撤销保留，不被重放")
    void revokedScopeIsNotReplayed() throws Exception {
        seed(); // 幂等：重复导入不改变已有数据
        UUID revoked = db.jdbc().queryForObject(
                "SELECT resource_id FROM user_resource_scope WHERE user_id = ? LIMIT 1",
                UUID.class, RESOURCE_ADMIN);
        db.jdbc().update(
                "DELETE FROM user_resource_scope WHERE user_id = ? AND resource_id = ?",
                RESOURCE_ADMIN, revoked);
        seed(); // 再次导入：已有用户应整体跳过，不得回填撤销的范围
        assertEquals(11L, db.managedResourceCount(RESOURCE_ADMIN),
                "再次导入后撤销的范围不得被重放");
        long stillGone = db.jdbc().queryForObject(
                "SELECT count(*) FROM user_resource_scope WHERE user_id = ? AND resource_id = ?",
                Long.class, RESOURCE_ADMIN, revoked);
        assertEquals(0L, stillGone, "被撤销的资源范围应保持删除");
    }

    @Test
    @Order(3)
    @DisplayName("删除用户后再次导入：新用户仍获得其配置范围")
    void newUserStillGetsScopes() throws Exception {
        // 整体删除 resource_admin（含其子表），使其在下一次导入时成为“新用户”
        db.jdbc().update("DELETE FROM user_resource_scope WHERE user_id = ?", RESOURCE_ADMIN);
        db.jdbc().update("DELETE FROM user_approval_scope WHERE user_id = ?", RESOURCE_ADMIN);
        db.jdbc().update("DELETE FROM user_permission WHERE user_id = ?", RESOURCE_ADMIN);
        db.jdbc().update("DELETE FROM user_role WHERE user_id = ?", RESOURCE_ADMIN);
        db.jdbc().update("DELETE FROM app_user WHERE id = ?", RESOURCE_ADMIN);
        seed();
        assertEquals(12L, db.managedResourceCount(RESOURCE_ADMIN),
                "重建后新用户应获得配置的 12 条资源范围授权");
    }

    // ------------------------------------------------------------------ 支撑

    private static void seed() throws Exception {
        newSeeder().run(null);
    }

    /** 手工装配 DemoDataSeeder（不启动完整 Spring 上下文），并注入 @Value 字段。 */
    private static DemoDataSeeder newSeeder() throws Exception {
        JdbcTemplate jdbc = db.jdbc();
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        TransactionTemplate tx = new TransactionTemplate(new DataSourceTransactionManager(db.dataSource()));
        DemoDataSeeder seeder = new DemoDataSeeder(jdbc, mapper, new BCryptPasswordEncoder(), tx);
        setField(seeder, "demoSeedFile", demoJsonPath());
        setField(seeder, "demoDate", DbTestSupport.DEMO_DATE.toString());
        setField(seeder, "demoPassword", requireDemoPassword());
        return seeder;
    }

    /** 演示口令从环境变量读取，不硬编码（03 §1/§3）；缺失时明确失败，不静默降级。 */
    private static String requireDemoPassword() {
        String value = System.getenv("DEMO_PASSWORD");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 DEMO_PASSWORD（先执行 scripts/load-env.ps1，03 §1/§3）");
        }
        return value;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = DemoDataSeeder.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    /** 兼容从仓库根或 server/ 目录运行 Maven 的两种工作目录。 */
    private static String demoJsonPath() {
        for (String candidate : new String[]{"test-data/demo.json", "../test-data/demo.json"}) {
            if (Files.isRegularFile(Path.of(candidate))) {
                return candidate;
            }
        }
        throw new IllegalStateException("找不到 test-data/demo.json");
    }
}
