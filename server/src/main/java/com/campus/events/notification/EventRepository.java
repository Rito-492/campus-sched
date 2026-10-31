package com.campus.events.notification;

import com.campus.events.contract.AuditView;
import com.campus.events.contract.EventDraft;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 事件基础设施持久层：只写 audit_log 与 outbox_event；通知表由 NotificationRepository 负责。 */
@Repository
public class EventRepository {

    /** 发件箱投递快照：消费者只按该名单投递，不重新计算收件人（02 §5）。 */
    public record OutboxPayload(List<UUID> recipientIds, String title, String body) {}

    public record OutboxRow(UUID id, UUID activityId, Integer versionNo, OutboxPayload payload, int attempts) {}

    private static final int MAX_ATTEMPTS = 5;

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper;

    public EventRepository(JdbcTemplate jdbc, ObjectMapper mapper) {
        this.jdbc = jdbc;
        this.mapper = mapper;
    }

    private String json(Object value) {
        try { return mapper.writeValueAsString(value); }
        catch (JsonProcessingException e) { throw new IllegalStateException("事件序列化失败", e); }
    }

    private OutboxPayload payload(String json) {
        try { return mapper.readValue(json, OutboxPayload.class); }
        catch (JsonProcessingException e) { throw new IllegalStateException("发件箱数据不符合统一契约", e); }
    }

    /** 审计只追加；audit_log 有 append-only 触发器兜底。 */
    public void appendAudit(EventDraft event) {
        jdbc.update("""
                INSERT INTO audit_log(id, activity_id, actor_id, event_type, version_no, summary)
                VALUES (?, ?, ?, ?, ?, ?)
                """, UUID.randomUUID(), event.activityId(), event.actorId(), event.eventType(),
                event.versionNo(), event.summary());
    }

    /** 追加发件箱事件并返回其 id（同时作为通知的 event_id）。 */
    public UUID appendOutbox(EventDraft event, List<UUID> recipients) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_event(id, activity_id, version_no, event_type, payload_json)
                VALUES (?, ?, ?, ?, ?::jsonb)
                """, id, event.activityId(), event.versionNo(), event.eventType(),
                json(new OutboxPayload(recipients, event.title(), event.body())));
        return id;
    }

    /** 认领一批到期待处理事件；SKIP LOCKED 避免多实例重复认领。 */
    public List<OutboxRow> claimNext(int limit) {
        return jdbc.query("""
                SELECT id, activity_id, version_no, payload_json, attempts FROM outbox_event
                WHERE status='PENDING' AND next_attempt_at <= now()
                ORDER BY next_attempt_at, id
                FOR UPDATE SKIP LOCKED LIMIT ?
                """, this::row, limit);
    }

    private OutboxRow row(ResultSet rs, int n) throws SQLException {
        return new OutboxRow(rs.getObject("id", UUID.class), rs.getObject("activity_id", UUID.class),
                (Integer) rs.getObject("version_no"), payload(rs.getString("payload_json")), rs.getInt("attempts"));
    }

    /** 投递成功：计入一次尝试并置 DONE。 */
    public void markDone(UUID id) {
        jdbc.update("""
                UPDATE outbox_event SET status='DONE', attempts=attempts+1, last_error=NULL, updated_at=now()
                WHERE id=?
                """, id);
    }

    /** 投递失败：指数退避 2/4/8/16/32s，第 5 次失败后置 FAILED。 */
    public void recordFailure(UUID id, String error) {
        jdbc.update("""
                UPDATE outbox_event SET attempts=attempts+1,
                       next_attempt_at=now()+(power(2, attempts+1)::int * interval '1 second'),
                       last_error=?, status=CASE WHEN attempts+1>=? THEN 'FAILED' ELSE 'PENDING' END,
                       updated_at=now() WHERE id=?
                """, error, MAX_ATTEMPTS, id);
    }

    public List<AuditView> listAudit(UUID activityId, int limit) {
        return jdbc.query("""
                SELECT id, activity_id, actor_id, event_type, version_no, summary, created_at
                FROM audit_log WHERE activity_id=? ORDER BY created_at DESC, id ASC LIMIT ?
                """, (rs, n) -> new AuditView(rs.getObject("id", UUID.class),
                rs.getObject("activity_id", UUID.class), rs.getObject("actor_id", UUID.class),
                rs.getString("event_type"), rs.getInt("version_no"), rs.getString("summary"),
                rs.getTimestamp("created_at").toInstant()), activityId, limit);
    }
}
