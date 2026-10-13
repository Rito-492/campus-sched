package com.campus.events.resource;

import com.campus.events.support.DbTestSupport;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * DS-01 schema 迁移验证（真实 PostgreSQL，Failsafe 运行 *IT）。
 *
 * <p>对应验收：DS-01 要求"在全新测试 schema 执行迁移并验证唯一键、非法数量、外键、
 * 第二个待处理改期的拒绝"。断言依据 02 契约 §4 与 01 基线 §4/§8。
 * SQL 级等价验证已于 2026-10-13 用 psql 脚本在 PostgreSQL 17 真库通过（见
 * docs/design/handoff.md），本类把同一组断言固化为可重复执行的集成测试。
 *
 * <p>装载数据：无（纯结构验证，测试内自行写入最小数据；类内共享 schema，基础数据幂等）。
 *
 * <p>SQLSTATE 对照：23505 唯一键冲突、23514 CHECK 违反、23503 外键违反、P0001 触发器 RAISE。
 *
 * <p>执行命令（在仓库根目录，待 PM 基础工程 pom/mvnw 到位后可运行）：
 * {@code ./server/mvnw.cmd -f server/pom.xml "-Dit.test=SchemaIT" verify}
 */
@DisplayName("DS-01 V1 迁移验证（SchemaIT）")
class SchemaIT {

    @RegisterExtension
    static final DbTestSupport db = DbTestSupport.create();

    private static final UUID ORG = UUID.fromString("00000000-0000-0000-0000-000000001001");
    private static final UUID USER = UUID.fromString("00000000-0000-0000-0000-000000002001");
    private static final UUID VENUE = UUID.fromString("00000000-0000-0000-0000-000000003101");
    private static final UUID EQUIP = UUID.fromString("00000000-0000-0000-0000-000000003201");
    private static final UUID ACTIVITY = UUID.fromString("00000000-0000-0000-0000-000000004001");

    private static JdbcTemplate jdbc() {
        return db.jdbc();
    }

    // ------------------------------------------------------------------ 1. 17 表齐全

    @Test
    @DisplayName("V1 迁移后 17 张表全部存在且无多余表")
    void tc01_allSeventeenTablesExist() {
        List<String> tables = jdbc().queryForList(
                "SELECT table_name FROM information_schema.tables " +
                        "WHERE table_schema = ? AND table_type = 'BASE TABLE' ORDER BY 1",
                String.class, jdbc().queryForObject("SELECT current_schema()", String.class));
        assertEquals(17, tables.size(), () -> "应为 17 张表，实际：" + tables);
        List.<String>of("organization", "app_user", "user_role", "auth_session", "command_receipt",
                "resource", "resource_fault", "activity", "activity_version", "activity_approval",
                "activity_registration", "reservation", "change_request", "change_confirmation",
                "audit_log", "outbox_event", "notification")
                .forEach(t -> assertTrue(tables.contains(t), "缺少表：" + t));
    }

    // ------------------------------------------------------------------ 2. 唯一键

    @Test
    @DisplayName("重复用户名、幂等回执主键、预约(活动/版本/资源)唯一、通知(event,收件人)唯一均被拒绝")
    void tc02_uniqueConstraintsRejectDuplicates() {
        ensureBaseData();
        assertUniqueRejected("重复用户名", "uq_app_user_username",
                "INSERT INTO app_user (id, organization_id, username, display_name, password_hash, enabled) " +
                        "VALUES (?,?, 'organizer_a', '重复用户名', 'x', true)",
                UUID.randomUUID(), ORG);

        jdbc().update("INSERT INTO command_receipt (actor_id, idempotency_key, route, request_hash) VALUES (?,?,?,?)",
                USER, "key-schema-it-1", "/api/v1/activities", "h1");
        assertUniqueRejected("幂等回执主键重复", "pk_command_receipt",
                "INSERT INTO command_receipt (actor_id, idempotency_key, route, request_hash) VALUES (?,?,?,?)",
                USER, "key-schema-it-1", "/api/v1/resources", "h2");

        insertReservation("00000000-0000-0000-0000-000000009001", ACTIVITY);
        assertUniqueRejected("预约(活动/版本/资源)重复", "uq_reservation_activity_version_resource",
                "INSERT INTO reservation (id, activity_id, version_no, resource_id, start_at, end_at, quantity, status) " +
                        "VALUES (?,?,1,?, '2030-10-20T09:30:00+08:00', '2030-10-20T11:30:00+08:00', 1, 'ACTIVE')",
                UUID.randomUUID(), ACTIVITY, VENUE);

        UUID eventId = UUID.randomUUID();
        jdbc().update("INSERT INTO outbox_event (id, activity_id, version_no, event_type, payload_json) VALUES (?,?,1,?,?)",
                eventId, ACTIVITY, "CHANGE_APPLIED", "{\"title\":\"x\"}");
        jdbc().update("INSERT INTO notification (id, event_id, recipient_id, title, body) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), eventId, USER, "t", "b");
        assertUniqueRejected("通知(event,收件人)重复", "uq_notification_event_recipient",
                "INSERT INTO notification (id, event_id, recipient_id, title, body) VALUES (?,?,?,?,?)",
                UUID.randomUUID(), eventId, USER, "t", "b");
    }

    // ------------------------------------------------------------------ 3. 非法数量与结构校验、外键

    @Test
    @DisplayName("resource 形状 CHECK、开放时段 CHECK、故障外键均按契约拒绝非法值")
    void tc03_resourceChecksRejectIllegalValues() {
        ensureBaseData();
        assertCheckRejected("venue 数量必须为 1", "ck_resource_shape",
                "INSERT INTO resource (id, kind, name, category, capacity, quantity, open_from, open_to) " +
                        "VALUES (?, 'VENUE', '坏场地', 'VENUE', 80, 2, '08:00', '22:00')",
                UUID.randomUUID());
        assertCheckRejected("equipment capacity 必须为 NULL", "ck_resource_shape",
                "INSERT INTO resource (id, kind, name, category, capacity, quantity, open_from, open_to) " +
                        "VALUES (?, 'EQUIPMENT', '坏器材', 'LMIC', 5, 2, '08:00', '22:00')",
                UUID.randomUUID());
        assertCheckRejected("开放时段必须递增", "ck_resource_open_window",
                "INSERT INTO resource (id, kind, name, category, capacity, quantity, open_from, open_to) " +
                        "VALUES (?, 'VENUE', '倒置时段', 'VENUE', 80, 1, '22:00', '08:00')",
                UUID.randomUUID());
        assertFkRejected("故障引用不存在资源", "fk_resource_fault_resource",
                "INSERT INTO resource_fault (id, resource_id, start_at, end_at, unavailable_quantity, reason, created_by) " +
                        "VALUES (?,?, '2030-10-20T09:00:00+08:00', '2030-10-20T10:00:00+08:00', 1, 'x', ?)",
                UUID.randomUUID(), UUID.randomUUID(), USER);
    }

    // ------------------------------------------------------------------ 4. 待处理改期唯一索引

    @Test
    @DisplayName("每活动最多一个待处理改期；终态(APPLIED)后允许再次创建待处理")
    void tc04_onlyOnePendingChangePerActivity() {
        ensureBaseData();
        UUID firstChange = UUID.randomUUID();
        insertChange(firstChange, "AWAITING_CONFIRMATIONS");

        assertUniqueRejected("同活动第二个待处理改期", "uq_change_request_pending_activity",
                "INSERT INTO change_request (id, activity_id, base_version_no, target_plan_json, impact_json, reason, status, created_by) " +
                        "VALUES (?,?,1,'{}','{}','x','PENDING_TEACHER',?)",
                UUID.randomUUID(), ACTIVITY, USER);

        // 终态后新待处理被允许，且当前待处理恰为 1
        jdbc().update("UPDATE change_request SET status = 'APPLIED', applied_version_no = 1 WHERE id = ?", firstChange);
        insertChange(UUID.randomUUID(), "PENDING_RESOURCE");
        Integer pendingCount = jdbc().queryForObject(
                "SELECT count(*) FROM change_request WHERE activity_id = ? AND status IN " +
                        "('AWAITING_CONFIRMATIONS','PENDING_TEACHER','PENDING_RESOURCE')",
                Integer.class, ACTIVITY);
        assertEquals(1, pendingCount, "终态后新建待处理应成功，且同时刻恰有 1 个待处理");
    }

    // ------------------------------------------------------------------ 5. 可延迟复合外键

    @Test
    @DisplayName("活动与首版本同事务写入被延迟外键放行；预约引用缺失版本在提交时被拒绝")
    void tc05_deferredFkBehavior() throws SQLException {
        // 正路径：同事务插入活动与首版本，提交成功
        UUID draftActivity = UUID.randomUUID();
        try (Connection conn = db.dataSource().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement insertActivity = conn.prepareStatement(
                    "INSERT INTO activity (id, organization_id, owner_id, status, version_no, effective_version_no, lock_version) VALUES (?,?,?,?,?,NULL,0)");
                 PreparedStatement insertVersion = conn.prepareStatement(
                         "INSERT INTO activity_version (activity_id, version_no, input_json, created_by) VALUES (?,?,?,?)")) {
                insertActivity.setObject(1, draftActivity);
                insertActivity.setObject(2, ORG);
                insertActivity.setObject(3, USER);
                insertActivity.setString(4, "DRAFT");
                insertActivity.setInt(5, 1);
                insertActivity.executeUpdate();
                insertVersion.setObject(1, draftActivity);
                insertVersion.setInt(2, 1);
                insertVersion.setString(3, "{\"title\":\"x\"}");
                insertVersion.setObject(4, USER);
                insertVersion.executeUpdate();
                conn.commit();
                assertFalse(conn.isClosed(), "活动+首版本同事务提交应成功（延迟外键放行）");
            } finally {
                conn.close();
            }
        }
        Integer versions = jdbc().queryForObject(
                "SELECT count(*) FROM activity_version WHERE activity_id = ?", Integer.class, draftActivity);
        assertEquals(1, versions, "同事务提交后活动版本可见");

        // 负路径：预约引用不存在的版本 → 登记成功；提交时延迟外键拒绝
        ensureBaseData();
        try (Connection conn = db.dataSource().getConnection()) {
            conn.setAutoCommit(false);
            try (PreparedStatement insertReservation = conn.prepareStatement(
                    "INSERT INTO reservation (id, activity_id, version_no, resource_id, start_at, end_at, quantity, status) " +
                            "VALUES (?,?,?,?,?,?,1,'ACTIVE')")) {
                insertReservation.setObject(1, UUID.randomUUID());
                insertReservation.setObject(2, ACTIVITY);
                insertReservation.setInt(3, 99);
                insertReservation.setObject(4, VENUE);
                insertReservation.setObject(5, OffsetDateTime.parse("2030-10-20T12:00:00+08:00"));
                insertReservation.setObject(6, OffsetDateTime.parse("2030-10-20T13:00:00+08:00"));
                insertReservation.executeUpdate();
                SQLException commitFailure = assertThrows(SQLException.class, conn::commit,
                        "预约引用不存在的版本应在提交时被延迟外键拒绝");
                assertTrue(commitFailure.getMessage().contains("fk_reservation_version"),
                        () -> "提交失败应为 fk_reservation_version，实际：" + commitFailure.getMessage());
            }
        }
    }

    // ------------------------------------------------------------------ 6. 不可变保护触发器

    @Test
    @DisplayName("activity_version 不可改删、change_request.target_plan_json 不可改、audit_log 只追加")
    void tc06_immutabilityTriggersRejectIllegalUpdates() {
        ensureBaseData();
        assertTriggerRejected("activity_version 更新", "activity_version 不可修改",
                "UPDATE activity_version SET input_json = ? WHERE activity_id = ?",
                "{\"x\":1}", ACTIVITY);

        UUID changeId = UUID.randomUUID();
        insertChange(changeId, "PENDING_TEACHER");
        assertTriggerRejected("target_plan_json 更新", "target_plan_json 不可修改",
                "UPDATE change_request SET target_plan_json = ? WHERE id = ?",
                "{\"x\":2}", changeId);

        UUID auditId = UUID.randomUUID();
        jdbc().update("INSERT INTO audit_log (id, activity_id, actor_id, event_type, version_no, summary) VALUES (?,?,?,?,?,?)",
                auditId, ACTIVITY, USER, "CHANGE_CREATED", 1, "s");
        assertTriggerRejected("audit_log 更新", "audit_log 只追加",
                "UPDATE audit_log SET summary = ? WHERE id = ?",
                "改写", auditId);
    }

    // ------------------------------------------------------------------ 插入辅助

    /** 类内测试共享一个 schema：基础数据只插入一次，后续测试幂等跳过。 */
    private static void ensureBaseData() {
        Integer orgCount = jdbc().queryForObject(
                "SELECT count(*) FROM organization WHERE id = ?", Integer.class, ORG);
        if (orgCount != null && orgCount > 0) {
            return;
        }
        jdbc().update("INSERT INTO organization (id, name) VALUES (?, '软件学院')", ORG);
        jdbc().update("INSERT INTO app_user (id, organization_id, username, display_name, password_hash, enabled) VALUES (?,?,?,?,?,true)",
                USER, ORG, "organizer_a", "组织者A", "x");
        jdbc().update("INSERT INTO resource (id, kind, name, category, capacity, quantity, open_from, open_to) VALUES (?, 'VENUE', '学术报告厅A', 'VENUE', 80, 1, '08:00', '22:00')", VENUE);
        jdbc().update("INSERT INTO resource (id, kind, name, category, capacity, quantity, open_from, open_to) VALUES (?, 'EQUIPMENT', '投影设备组A', 'PROJECTOR', NULL, 2, '08:00', '22:00')", EQUIP);
        jdbc().update("INSERT INTO activity (id, organization_id, owner_id, status, version_no, effective_version_no, lock_version) VALUES (?, ?, ?, 'SCHEDULED', 1, 1, 0)",
                ACTIVITY, ORG, USER);
        jdbc().update("INSERT INTO activity_version (activity_id, version_no, input_json, created_by) VALUES (?, 1, ?, ?)",
                ACTIVITY, "{\"title\":\"A\"}", USER);
    }

    private static void insertReservation(String reservationId, UUID activityId) {
        jdbc().update("INSERT INTO reservation (id, activity_id, version_no, resource_id, start_at, end_at, quantity, status) " +
                "VALUES (?,?,?,?,?,?,1,'ACTIVE')", UUID.fromString(reservationId), activityId, VENUE,
                OffsetDateTime.parse("2030-10-20T09:30:00+08:00"),
                OffsetDateTime.parse("2030-10-20T11:30:00+08:00"));
    }

    private static void insertChange(UUID changeId, String status) {
        jdbc().update("INSERT INTO change_request (id, activity_id, base_version_no, target_plan_json, impact_json, reason, status, created_by) " +
                "VALUES (?,?,1,'{}','{}','x', ?, ?)", changeId, ACTIVITY, status, USER);
    }

    // ------------------------------------------------------------------ 拒绝断言

    /** 断言语句被唯一索引拒绝（SQLSTATE 23505）。 */
    private static void assertUniqueRejected(String description, String constraintName, String sql, Object... args) {
        assertRejected(description, "23505", constraintName, sql, args);
    }

    /** 断言语句被 CHECK 约束拒绝（SQLSTATE 23514）。 */
    private static void assertCheckRejected(String description, String constraintName, String sql, Object... args) {
        assertRejected(description, "23514", constraintName, sql, args);
    }

    /** 断言语句被外键拒绝（SQLSTATE 23503）。 */
    private static void assertFkRejected(String description, String constraintName, String sql, Object... args) {
        assertRejected(description, "23503", constraintName, sql, args);
    }

    /** 断言语句被触发器 RAISE 拒绝（SQLSTATE P0001），消息包含登记的保护语义。 */
    private static void assertTriggerRejected(String description, String expectedMessagePart, String sql, Object... args) {
        assertRejected(description, "P0001", expectedMessagePart, sql, args);
    }

    /**
     * 执行语句并断言被数据库拒绝：SQLSTATE 精确匹配、错误消息包含期望约束名/语义，
     * 防止"恰好因为别的原因失败"造成的假通过。
     */
    private static void assertRejected(String description, String expectedSqlState,
                                       String expectedMessagePart, String sql, Object... args) {
        DataAccessException failure = assertThrows(DataAccessException.class, () -> jdbc().update(sql, args),
                () -> description + " 应被数据库拒绝");
        Throwable root = failure;
        while (root.getCause() != null && !(root instanceof SQLException)) {
            root = root.getCause();
        }
        assertTrue(root instanceof SQLException, () -> description + " 的根因应为 SQLException，实际：" + failure);
        SQLException sqlEx = (SQLException) root;
        assertEquals(expectedSqlState, sqlEx.getSQLState(),
                () -> description + " 的 SQLSTATE 应为 " + expectedSqlState + "，实际：" + sqlEx.getSQLState());
        assertTrue(sqlEx.getMessage() != null && sqlEx.getMessage().contains(expectedMessagePart),
                () -> description + " 的错误消息应包含「" + expectedMessagePart + "」，实际：" + sqlEx.getMessage());
    }
}
