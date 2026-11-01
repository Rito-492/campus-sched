package com.campus.events.notification;

import com.campus.events.contract.NotificationView;
import com.campus.events.foundation.ApiException;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

/** 站内通知持久层：消费者写入去重，用户只读本人记录。 */
@Repository
public class NotificationRepository {
    private final JdbcTemplate jdbc;

    public NotificationRepository(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** 幂等投递：event_id+recipient_id 唯一，重复投递静默忽略。 */
    public void insert(UUID eventId, UUID recipientId, UUID activityId, Integer versionNo, String title, String body) {
        jdbc.update("""
                INSERT INTO notification(id, event_id, recipient_id, activity_id, version_no, title, body)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                ON CONFLICT (event_id, recipient_id) DO NOTHING
                """, UUID.randomUUID(), eventId, recipientId, activityId, versionNo, title, body);
    }

    public List<NotificationView> list(UUID recipientId, boolean unreadOnly, int limit) {
        String sql = "SELECT id, event_id, activity_id, version_no, title, body, read_at, created_at "
                + "FROM notification WHERE recipient_id=?" + (unreadOnly ? " AND read_at IS NULL" : "")
                + " ORDER BY created_at DESC, id ASC LIMIT ?";
        return jdbc.query(sql, this::view, recipientId, limit);
    }

    public NotificationView get(UUID id, UUID recipientId) {
        var rows = jdbc.query("""
                SELECT id, event_id, activity_id, version_no, title, body, read_at, created_at
                FROM notification WHERE id=? AND recipient_id=?
                """, this::view, id, recipientId);
        if (rows.isEmpty()) throw ApiException.notFound();
        return rows.getFirst();
    }

    public void markRead(UUID id, UUID recipientId) {
        int changed = jdbc.update("""
                UPDATE notification SET read_at=COALESCE(read_at, now()), updated_at=now()
                WHERE id=? AND recipient_id=?
                """, id, recipientId);
        if (changed != 1) throw ApiException.notFound();
    }

    private NotificationView view(ResultSet rs, int n) throws SQLException {
        return new NotificationView(rs.getObject("id", UUID.class), rs.getObject("event_id", UUID.class),
                rs.getObject("activity_id", UUID.class), (Integer) rs.getObject("version_no"),
                rs.getString("title"), rs.getString("body"),
                rs.getTimestamp("read_at") == null ? null : rs.getTimestamp("read_at").toInstant(),
                rs.getTimestamp("created_at").toInstant());
    }
}
