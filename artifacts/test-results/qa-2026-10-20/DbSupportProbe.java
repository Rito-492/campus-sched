import com.campus.events.support.DbTestSupport;
import org.springframework.jdbc.core.JdbcTemplate;

public class DbSupportProbe {
    private static long count(JdbcTemplate jdbc, String table) {
        return jdbc.queryForObject("SELECT count(*) FROM " + table, Long.class);
    }
    private static void require(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            System.setProperty("test.data.dir", args[0]);
        }
        DbTestSupport demo = DbTestSupport.create("demo");
        try {
            demo.beforeAll(null);
            JdbcTemplate jdbc = demo.jdbc();
            require(count(jdbc, "organization") == 2, "demo organizations != 2");
            require(count(jdbc, "app_user") == 9, "demo users != 9");
            require(count(jdbc, "resource") == 12, "demo resources != 12");
            require(count(jdbc, "user_resource_scope") == 12, "managed resource scopes != 12");
            require(count(jdbc, "activity") == 2, "demo activities != 2");
            require(count(jdbc, "activity_version") == 2, "demo activity versions != 2");
            String jsonType = jdbc.queryForObject("SELECT jsonb_typeof(input_json) FROM activity_version LIMIT 1", String.class);
            require("object".equals(jsonType), "input_json did not persist as JSON object");
            System.out.println("DEMO_LOAD_OK org=2 users=9 resources=12 resourceScopes=12 activities=2 versions=2 jsonb=" + jsonType);
        } finally {
            demo.afterAll(null);
        }

        DbTestSupport reference = DbTestSupport.create();
        try {
            reference.beforeAll(null);
            reference.loadReferenceData("demo");
            require(count(reference.jdbc(), "organization") == 2, "reference organizations != 2");
            require(count(reference.jdbc(), "app_user") == 9, "reference users != 9");
            require(count(reference.jdbc(), "resource") == 12, "reference resources != 12");
            require(count(reference.jdbc(), "activity") == 0, "reference load included activities");
            require(count(reference.jdbc(), "activity_registration") == 0, "reference load included registrations");
            System.out.println("REFERENCE_LOAD_OK org=2 users=9 resources=12 activities=0 registrations=0");
        } finally {
            reference.afterAll(null);
        }

        DbTestSupport atomic = DbTestSupport.create();
        try {
            atomic.beforeAll(null);
            boolean failed = false;
            try {
                atomic.loadTestData("atomic-fail");
            } catch (IllegalStateException expected) {
                failed = true;
            }
            require(failed, "invalid registration should fail");
            require(count(atomic.jdbc(), "organization") == 0, "failed load left organizations behind");
            require(count(atomic.jdbc(), "app_user") == 0, "failed load left users behind");
            require(count(atomic.jdbc(), "resource") == 0, "failed load left resources behind");
            require(count(atomic.jdbc(), "activity") == 0, "failed load left activities behind");
            require(count(atomic.jdbc(), "activity_version") == 0, "failed load left activity versions behind");
            System.out.println("ATOMIC_ROLLBACK_OK invalid FK failure left no partial rows");
        } finally {
            atomic.afterAll(null);
        }
    }
}
