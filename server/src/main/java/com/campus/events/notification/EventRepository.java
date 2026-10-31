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

    /** claimToken 是本次认领的唯一所有权令牌；记账必须携带它，否则视为丢失所有权（01 §5、02 §5）。 */
    public record OutboxRow(UUID id, UUID activityId, Integer versionNo, OutboxPayload payload, int attempts, UUID claimToken) {}

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

    /** 认领租约秒数：认领即把 next_attempt_at 推到未来，投递与记账应在租约内完成，超时/崩溃后自动恢复重认领。 */
    private static final int LEASE_SECONDS = 60;

    /**
     * 原子认领一批到期待处理事件：以 FOR UPDATE SKIP LOCKED 选出待处理行，并在同一语句把 next_attempt_at
     * 推后作为「认领租约」，同时生成本次认领唯一的 claim_token。事务提交后租约与令牌持久化，另一实例的
     * claimNext 因 next_attempt_at 未到期而跳过；成功/失败记账须匹配 claim_token，租约过期被重认领后旧认领者
     * 的记账因令牌不匹配而影响 0 行（01 §5、02 §5），阻止僵尸消费者篡改新认领者的状态与重试预算。
     */
    public List<OutboxRow> claimNext(int limit) {
        return jdbc.query("""
                WITH claimed AS (
                    SELECT id FROM outbox_event
                    WHERE status='PENDING' AND next_attempt_at <= now()
                    ORDER BY next_attempt_at, id
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                UPDATE outbox_event o
                SET next_attempt_at = now() + (?::int * interval '1 second'),
                    claim_token = gen_random_uuid()
                FROM claimed c
                WHERE o.id = c.id
                RETURNING o.id, o.activity_id, o.version_no, o.payload_json, o.attempts, o.claim_token
                """, this::row, limit, LEASE_SECONDS);
    }

    private OutboxRow row(ResultSet rs, int n) throws SQLException {
        return new OutboxRow(rs.getObject("id", UUID.class), rs.getObject("activity_id", UUID.class),
                (Integer) rs.getObject("version_no"), payload(rs.getString("payload_json")), rs.getInt("attempts"),
                rs.getObject("claim_token", UUID.class));
    }

    /**
     * 投递成功：计入一次尝试并置 DONE；仅对仍为 PENDING 且持有本次认领令牌的事件生效。
     * 返回是否实际生效；false 表示已丢失所有权（租约过期被重认领或已被终态），调用方不得当作投递成功提交。
     */
    public boolean markDone(UUID id, UUID claimToken) {
        return jdbc.update("""
                UPDATE outbox_event SET status='DONE', attempts=attempts+1, last_error=NULL, updated_at=now()
                WHERE id=? AND status='PENDING' AND claim_token=?
                """, id, claimToken) == 1;
    }

    /**
     * 投递失败：指数退避 2/4/8/16/32s，第 5 次失败后置 FAILED；仅对仍为 PENDING 且持有本次认领令牌的事件生效，
     * 避免覆盖终态或篡改新认领者的重试预算。返回是否实际生效；false 表示已丢失所有权，调用方应静默放弃。
     */
    public boolean recordFailure(UUID id, UUID claimToken, String error) {
        return jdbc.update("""
                UPDATE outbox_event SET attempts=attempts+1,
                       next_attempt_at=now()+(power(2, attempts+1)::int * interval '1 second'),
                       last_error=?, status=CASE WHEN attempts+1>=? THEN 'FAILED' ELSE 'PENDING' END,
                       updated_at=now() WHERE id=? AND status='PENDING' AND claim_token=?
                """, error, MAX_ATTEMPTS, id, claimToken) == 1;
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
