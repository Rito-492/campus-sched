package com.campus.events.acceptance;

import com.campus.events.dev.DemoDataSeeder;
import com.campus.events.support.DbTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.transaction.support.TransactionTemplate;

import java.lang.reflect.Field;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** QA补充Seeder失败原子性回归：真实PostgreSQL及隔离schema。 */
class SeederRollbackIT {

    @RegisterExtension
    static final DbTestSupport db = DbTestSupport.create();

    @Test
    void lateRegistrationForeignKeyFailureRollsBackEntireSeed(@TempDir Path tempDir) throws Exception {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var root = mapper.readTree(Files.readString(demoJsonPath()));
        assertEquals(1, root.path("registrations").size(), "演示数据需有报名行以触发末尾失败");
        ((ObjectNode) root.path("registrations").get(0)).put(
                "userId", "00000000-0000-0000-0000-000000009999");
        Path invalidSeed = tempDir.resolve("demo-invalid-registration.json");
        mapper.writeValue(invalidSeed.toFile(), root);

        DemoDataSeeder seeder = new DemoDataSeeder(
                db.jdbc(), mapper, new BCryptPasswordEncoder(),
                new TransactionTemplate(new DataSourceTransactionManager(db.dataSource())));
        setField(seeder, "demoSeedFile", invalidSeed.toString());
        setField(seeder, "demoDate", DbTestSupport.DEMO_DATE.toString());
        setField(seeder, "demoPassword", requiredDemoPassword());

        assertThrows(RuntimeException.class, () -> seeder.run(null),
                "末尾报名外键失败必须让Seeder整体事务回滚");

        JdbcTemplate jdbc = db.jdbc();
        assertEquals(0L, count(jdbc, "organization"));
        assertEquals(0L, count(jdbc, "app_user"));
        assertEquals(0L, count(jdbc, "user_role"));
        assertEquals(0L, count(jdbc, "resource"));
        assertEquals(0L, count(jdbc, "user_resource_scope"));
        assertEquals(0L, count(jdbc, "activity"));
        assertEquals(0L, count(jdbc, "activity_version"));
        assertEquals(0L, count(jdbc, "reservation"));
        assertEquals(0L, count(jdbc, "activity_registration"));
    }

    private static long count(JdbcTemplate jdbc, String table) {
        Long value = jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
        return value == null ? 0 : value;
    }

    private static void setField(Object target, String name, Object value) throws Exception {
        Field field = DemoDataSeeder.class.getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static String requiredDemoPassword() {
        String value = System.getenv("DEMO_PASSWORD");
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("缺少环境变量 DEMO_PASSWORD；不得使用源码默认密码");
        }
        return value;
    }

    private static Path demoJsonPath() {
        for (String candidate : new String[]{"test-data/demo.json", "../test-data/demo.json"}) {
            Path path = Path.of(candidate);
            if (Files.isRegularFile(path)) return path;
        }
        throw new IllegalStateException("找不到 test-data/demo.json");
    }
}
