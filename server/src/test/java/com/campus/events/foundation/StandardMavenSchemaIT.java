package com.campus.events.foundation;

import com.campus.events.CampusEventsApplication;
import com.campus.events.support.DbTestSupport;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import static org.junit.jupiter.api.Assertions.*;

/** 使用QA原有扩展但不装载其演示fixture，真实验证标准Maven的Spring/Flyway schema绑定。 */
@SpringBootTest(classes = CampusEventsApplication.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class StandardMavenSchemaIT {
    @RegisterExtension static final DbTestSupport db = DbTestSupport.create();
    @Autowired JdbcTemplate jdbc;
    @Test void springAndFlywayUseTheSameRandomSchemaAsTestSupport() {
        assertEquals("campus_test", jdbc.queryForObject("SELECT current_database()", String.class));
        assertEquals(db.schemaName(), jdbc.queryForObject("SELECT current_schema()", String.class));
        assertEquals(22, jdbc.queryForObject("SELECT count(*) FROM information_schema.tables WHERE table_schema=? AND table_type='BASE TABLE' AND table_name<>'flyway_schema_history'", Integer.class, db.schemaName()));
        assertEquals(0, jdbc.queryForObject("SELECT count(*) FROM app_user", Integer.class));
    }
}
