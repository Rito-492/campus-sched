package com.campus.events.support;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** QA-01 测试数据装载回归：真实 PostgreSQL、隔离 schema、V1 即时/延迟外键。 */
class DbTestSupportIT {

    @RegisterExtension
    static final DbTestSupport db = DbTestSupport.create("demo");

    @Test
    void demoLoadsWithResourceScopesActivityVersionsAndJsonb() {
        var jdbc = db.jdbc();

        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM organization", Long.class));
        assertEquals(9L, jdbc.queryForObject("SELECT count(*) FROM app_user", Long.class));
        assertEquals(12L, jdbc.queryForObject("SELECT count(*) FROM resource", Long.class));
        assertEquals(12L, jdbc.queryForObject("SELECT count(*) FROM user_resource_scope", Long.class));
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM activity", Long.class));
        assertEquals(2L, jdbc.queryForObject("SELECT count(*) FROM activity_version", Long.class));
        assertEquals(2L, jdbc.queryForObject(
                "SELECT count(*) FROM activity_version WHERE jsonb_typeof(input_json) = 'object'", Long.class));
    }
}
