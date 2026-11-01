package com.campus.events.change;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 改期模块持久层：只写 change_request 与 change_confirmation；审计/通知经 EventPort 追加。 */
@Repository
public class ChangeRepository {

    public record Row(UUID id, UUID activityId, int baseVersionNo, Plan targetPlan, Impact impact,
                      String reason, ChangeStatus status, DecisionRecord approverDecision,
                      DecisionRecord resourceDecision, List<Conflict> lastConflicts,
                      Integer appliedVersionNo, UUID createdBy, Instant createdAt, Instant updatedAt) {}

    private static final String SELECT = """
            SELECT id, activity_id, base_version_no, target_plan_json, impact_json, reason, status,
                   approver_decision_json, resource_decision_json, last_conflicts_json, applied_version_no,
                   created_by, created_at, updated_at
            FROM change_request
            """;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public ChangeRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    private Row row(ResultSet rs, int n) throws SQLException {
        return new Row(
                rs.getObject("id", UUID.class),
                rs.getObject("activity_id", UUID.class),
                rs.getInt("base_version_no"),
                read(rs.getString("target_plan_json"), Plan.class),
                read(rs.getString("impact_json"), Impact.class),
                rs.getString("reason"),
                ChangeStatus.valueOf(rs.getString("status")),
                readOrNull(rs.getString("approver_decision_json"), DecisionRecord.class),
                readOrNull(rs.getString("resource_decision_json"), DecisionRecord.class),
                conflicts(rs.getString("last_conflicts_json")),
                (Integer) rs.getObject("applied_version_no"),
                rs.getObject("created_by", UUID.class),
                rs.getTimestamp("created_at").toInstant(),
                rs.getTimestamp("updated_at").toInstant());
    }

    private <T> T read(String json, Class<T> type) {
        try { return mapper.readValue(json, type); }
        catch (JsonProcessingException e) { throw new IllegalStateException("改期数据不符合统一契约", e); }
    }

    private <T> T readOrNull(String json, Class<T> type) {
        if (json == null) return null;
        return read(json, type);
    }

    private List<Conflict> conflicts(String json) {
        if (json == null) return List.of();
        try { return mapper.readValue(json, new TypeReference<List<Conflict>>() {}); }
        catch (JsonProcessingException e) { throw new IllegalStateException("冲突数据不符合统一契约", e); }
    }

    private String json(Object value) {
        if (value == null) return null;
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("改期序列化失败", e); }
    }

    public Row get(UUID id) {
        var rows = jdbc.query(SELECT + "WHERE id=?", this::row, id);
        if (rows.isEmpty()) throw ApiException.notFound();
        return rows.getFirst();
    }

    public Row lock(UUID id) {
        var ids = jdbc.query("SELECT id FROM change_request WHERE id=? FOR UPDATE",
                (rs, n) -> rs.getObject(1, UUID.class), id);
        if (ids.isEmpty()) throw ApiException.notFound();
        return get(id);
    }

    /** 只读活动归属，供上层按「活动→改期」锁序先锁活动再锁改期。 */
    public UUID activityId(UUID changeId) {
        var ids = jdbc.query("SELECT activity_id FROM change_request WHERE id=?",
                (rs, n) -> rs.getObject(1, UUID.class), changeId);
        if (ids.isEmpty()) throw ApiException.notFound();
        return ids.getFirst();
    }

    public UUID pendingId(UUID activityId) {
        var ids = jdbc.query("""
                SELECT id FROM change_request WHERE activity_id=? AND status IN
                ('AWAITING_CONFIRMATIONS','PENDING_APPROVER','PENDING_RESOURCE')
                """, (rs, n) -> rs.getObject(1, UUID.class), activityId);
        return ids.isEmpty() ? null : ids.getFirst();
    }

    public boolean hasPending(UUID activityId) { return pendingId(activityId) != null; }

    public List<Row> list(UUID activityId) {
        return jdbc.query(SELECT + "WHERE activity_id=? ORDER BY created_at DESC, id ASC LIMIT 201",
                this::row, activityId);
    }

    public Row create(UUID id, UUID activityId, int baseVersionNo, Plan targetPlan, Impact impact,
                      String reason, ChangeStatus status, UUID createdBy) {
        jdbc.update("""
                INSERT INTO change_request(id, activity_id, base_version_no, target_plan_json,
                        impact_json, reason, status, created_by)
                VALUES (?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?)
                """, id, activityId, baseVersionNo, json(targetPlan), json(impact), reason,
                status.name(), createdBy);
        return get(id);
    }

    public void save(Row row, ChangeStatus status, DecisionRecord approver, DecisionRecord resource,
                     List<Conflict> conflicts, Integer appliedVersionNo) {
        jdbc.update("""
                UPDATE change_request SET status=?, approver_decision_json=?::jsonb,
                       resource_decision_json=?::jsonb, last_conflicts_json=?::jsonb,
                       applied_version_no=?, updated_at=now() WHERE id=?
                """, status.name(), json(approver), json(resource), json(conflicts), appliedVersionNo, row.id());
    }

    public void createConfirmations(UUID changeId, List<UUID> staffIds) {
        for (UUID staffId : staffIds) {
            jdbc.update("""
                    INSERT INTO change_confirmation(change_id, user_id, decision, comment, decided_at)
                    VALUES (?, ?, NULL, '', NULL)
                    """, changeId, staffId);
        }
    }

    public void decideConfirmation(UUID changeId, UUID userId, Decision decision, String comment, Instant decidedAt) {
        int changed = jdbc.update("""
                UPDATE change_confirmation SET decision=?, comment=?, decided_at=?, updated_at=now()
                WHERE change_id=? AND user_id=? AND decision IS NULL
                """, decision.name(), comment, Timestamp.from(decidedAt), changeId, userId);
        if (changed != 1) throw new ApiException(409, "ALREADY_DECIDED", "该工作人员已确认过此改期");
    }

    public boolean allConfirmed(UUID changeId) {
        Integer pending = jdbc.queryForObject(
                "SELECT count(*) FROM change_confirmation WHERE change_id=? AND decision IS NULL",
                Integer.class, changeId);
        return pending != null && pending == 0;
    }

    public List<Confirmation> confirmations(UUID changeId) {
        return jdbc.query("""
                SELECT user_id, decision, comment, decided_at FROM change_confirmation
                WHERE change_id=? ORDER BY user_id
                """, (rs, n) -> new Confirmation(
                        rs.getObject("user_id", UUID.class),
                        rs.getString("decision") == null ? null : Decision.valueOf(rs.getString("decision")),
                        rs.getString("comment"),
                        rs.getTimestamp("decided_at") == null ? null : rs.getTimestamp("decided_at").toInstant()),
                changeId);
    }

    /** 活动取消时仅把待处理改期置为 CANCELLED_BY_ACTIVITY；不自行递增活动锁（由取消调用者统一递增）。 */
    public void cancelPending(UUID activityId, UUID actorId, String reason) {
        jdbc.update("""
                UPDATE change_request SET status='CANCELLED_BY_ACTIVITY', updated_at=now()
                WHERE activity_id=? AND status IN
                ('AWAITING_CONFIRMATIONS','PENDING_APPROVER','PENDING_RESOURCE')
                """, activityId);
    }
}
