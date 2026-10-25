package com.campus.events.activity;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.*;

/** 只写活动、不可变版本和审批表；资源与改期只通过端口修改。 */
@Repository
public class ActivityRepository {
    public record Row(UUID id, UUID organizationId, UUID ownerId, ActivityStatus status, int versionNo,
                      Integer effectiveVersionNo, int lockVersion, ActivityInput input, Instant createdAt) {}
    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;
    private final Object locksKey = new Object();
    private static final String SELECT = "SELECT a.*,v.input_json FROM activity a JOIN activity_version v ON v.activity_id=a.id AND v.version_no=a.version_no ";
    public ActivityRepository(JdbcTemplate jdbc, ObjectMapper mapper) { this.jdbc = jdbc; this.mapper = mapper; }
    private Row row(ResultSet rs, int n) throws SQLException {
        try {
            return new Row(rs.getObject("id", UUID.class), rs.getObject("organization_id", UUID.class),
                    rs.getObject("owner_id", UUID.class), ActivityStatus.valueOf(rs.getString("status")),
                    rs.getInt("version_no"), (Integer) rs.getObject("effective_version_no"), rs.getInt("lock_version"),
                    mapper.readValue(rs.getString("input_json"), ActivityInput.class), rs.getTimestamp("created_at").toInstant());
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("活动版本解析失败", e); }
    }
    public Row get(UUID id) {
        var rows = jdbc.query(SELECT + "WHERE a.id=?", this::row, id);
        if (rows.isEmpty()) throw ApiException.notFound();
        return rows.getFirst();
    }
    public Row lock(UUID id) {
        var held=locks();
        // 先锁主行，再用新语句读取版本；避免等待期间切换版本使JOIN的旧快照丢行。
        var ids=jdbc.query("SELECT id FROM activity WHERE id=? FOR UPDATE",(rs,n)->rs.getObject(1,UUID.class),id);
        if(ids.isEmpty()) throw ApiException.notFound();
        held.add(id); return get(id);
    }
    @SuppressWarnings("unchecked") private Set<UUID> locks() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()) throw new IllegalStateException("活动写入需要已有事务");
        var locks = (Set<UUID>) TransactionSynchronizationManager.getResource(locksKey);
        if (locks == null) {
            locks = new HashSet<>(); var bound = locks;
            TransactionSynchronizationManager.bindResource(locksKey, locks);
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void suspend() { TransactionSynchronizationManager.unbindResource(locksKey); }
                @Override public void resume() { TransactionSynchronizationManager.bindResource(locksKey, bound); }
                @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResourceIfPossible(locksKey); }
            });
        }
        return locks;
    }
    public void requireLock(UUID id) { if (!locks().contains(id)) throw new IllegalStateException("调用者必须先锁定活动"); }
    public UUID create(Actor owner, ActivityInput input) {
        UUID id = UUID.randomUUID();
        jdbc.update("INSERT INTO activity(id,organization_id,owner_id,status,version_no,lock_version) VALUES (?,?,?,'DRAFT',1,0)", id, owner.organizationId(), owner.id());
        version(id, 1, input, owner.id());
        return id;
    }
    public void version(UUID id, int version, ActivityInput input, UUID actor) {
        try { jdbc.update("INSERT INTO activity_version(activity_id,version_no,input_json,created_by) VALUES (?,?,?::jsonb,?)", id, version, mapper.writeValueAsString(input), actor); }
        catch (com.fasterxml.jackson.core.JsonProcessingException e) { throw new IllegalStateException("活动版本序列化失败", e); }
    }
    public void update(Row before, ActivityStatus status, int version, Integer effective) {
        requireLock(before.id());
        int changed = jdbc.update("UPDATE activity SET status=?,version_no=?,effective_version_no=?,lock_version=lock_version+1,updated_at=now() WHERE id=? AND lock_version=?",
                status.name(), version, effective, before.id(), before.lockVersion());
        if (changed != 1) throw new ApiException(409, "STALE_VERSION", "活动已被其他操作修改");
    }
    public void approval(Row row, UUID actor, ApprovalCommand command) {
        jdbc.update("INSERT INTO activity_approval(id,activity_id,version_no,actor_id,decision,comment) VALUES (?,?,?,?,?,?)",
                UUID.randomUUID(), row.id(), row.versionNo(), actor, command.decision().name(), command.comment());
    }
    /** 授权在SQL中先过滤，再读取201行，避免截断之前漏掉有权查看的数据。 */
    public List<Row> list(Actor actor, String scope, ActivityStatus status) {
        String filter = switch (scope) {
            case "mine" -> "a.owner_id=?";
            case "public" -> "a.status='OPEN' AND ?::uuid IS NOT NULL";
            case "review" -> """
                a.owner_id<>? AND a.status IN ('PENDING_APPROVER','APPROVED_UNRESERVED','SCHEDULED','OPEN') AND (
                  (EXISTS(SELECT 1 FROM user_role WHERE user_id=? AND role='APPROVER') AND NOT EXISTS(
                    SELECT 1 FROM resource r WHERE r.id IN (
                      SELECT (v.input_json#>>'{plan,venueId}')::uuid UNION ALL
                      SELECT (e->>'resourceId')::uuid FROM jsonb_array_elements(v.input_json#>'{plan,equipment}') e)
                    AND NOT EXISTS(SELECT 1 FROM user_approval_scope s WHERE s.user_id=? AND s.organization_id=a.organization_id AND s.resource_category=r.category)))
                  OR (EXISTS(SELECT 1 FROM user_role WHERE user_id=? AND role='ADMIN')
                    AND EXISTS(SELECT 1 FROM user_permission WHERE user_id=? AND permission='RESOURCE_CONFIRM') AND NOT EXISTS(
                      SELECT 1 FROM (SELECT (v.input_json#>>'{plan,venueId}')::uuid id UNION ALL
                        SELECT (e->>'resourceId')::uuid FROM jsonb_array_elements(v.input_json#>'{plan,equipment}') e) needed
                      WHERE NOT EXISTS(SELECT 1 FROM user_resource_scope s WHERE s.user_id=? AND s.resource_id=needed.id))))
                """;
            default -> throw ApiException.invalid("scope必须为mine/public/review");
        };
        var args = new ArrayList<Object>();
        for (int i = 0; i < (scope.equals("review") ? 6 : 1); i++) args.add(actor.id());
        args.add(status == null ? null : status.name()); args.add(status == null ? null : status.name());
        return jdbc.query(SELECT + "WHERE " + filter + " AND (?::text IS NULL OR a.status=?) ORDER BY a.created_at DESC,a.id LIMIT 201", this::row, args.toArray());
    }
}
