package com.campus.events.notification;

import com.campus.events.support.DbTestSupport;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.RegisterExtension;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.junit.jupiter.api.Assertions.*;

/**
 * 发件箱认领互斥回归（评审 P2）。
 *
 * <p>认领必须以持久化租约保证多消费者互斥（而非 SELECT 后锁在另一事务里失效），
 * 成功/失败记账只作用于仍为 PENDING 的事件，晚到结果不得覆盖 DONE/FAILED 终态。
 */
class OutboxClaimIT {
    @RegisterExtension
    static final DbTestSupport db = DbTestSupport.create();

    EventRepository events;
    JdbcTemplate jdbc;

    @BeforeEach void setup() {
        jdbc = db.jdbc();
        events = new EventRepository(jdbc, new ObjectMapper());
    }

    private UUID insertPending(int attempts) {
        UUID id = UUID.randomUUID();
        jdbc.update("""
                INSERT INTO outbox_event(id, activity_id, version_no, event_type, payload_json, status, attempts, next_attempt_at)
                VALUES (?, NULL, 0, 'TEST', '{"recipientIds":[],"title":"t","body":"b"}'::jsonb, 'PENDING', ?, now())
                """, id, attempts);
        return id;
    }

    private String statusOf(UUID id) {
        return jdbc.queryForObject("SELECT status FROM outbox_event WHERE id=?", String.class, id);
    }
    private int attemptsOf(UUID id) {
        Integer n = jdbc.queryForObject("SELECT attempts FROM outbox_event WHERE id=?", Integer.class, id);
        return n == null ? 0 : n;
    }
    private Instant nextAttemptOf(UUID id) {
        return jdbc.queryForObject("SELECT next_attempt_at FROM outbox_event WHERE id=?", Timestamp.class, id).toInstant();
    }

    @Test void claimLeasePreventsSecondConsumerFromClaimingSameEvent() {
        UUID id = insertPending(0);
        var claimed = events.claimNext(20);
        assertEquals(1, claimed.size());
        assertEquals(id, claimed.get(0).id());
        // 认领即把 next_attempt_at 推到未来作为持久化租约（事务提交后仍生效）。
        assertTrue(nextAttemptOf(id).isAfter(Instant.now()), "认领应持久化租约（next_attempt_at 在未来）");

        // 第二消费者在租约内不能再认领同一事件。
        assertTrue(events.claimNext(20).isEmpty(), "租约期间第二消费者不得认领同一事件");
    }

    @Test void recordFailureDoesNotOverwriteDoneState() {
        UUID id = insertPending(0);
        assertEquals(id, events.claimNext(20).get(0).id());

        events.markDone(id);
        assertEquals("DONE", statusOf(id));
        assertEquals(1, attemptsOf(id));

        // 晚到的失败记账不得把 DONE 覆盖回 PENDING/FAILED，也不得重复递增 attempts。
        events.recordFailure(id, "late error");
        assertEquals("DONE", statusOf(id));
        assertEquals(1, attemptsOf(id));
    }

    @Test void recordFailureBacksOffAndKeepsPendingForRetry() {
        UUID id = insertPending(0);
        assertEquals(id, events.claimNext(20).get(0).id());

        events.recordFailure(id, "boom");
        assertEquals("PENDING", statusOf(id));
        assertEquals(1, attemptsOf(id));
        assertTrue(nextAttemptOf(id).isAfter(Instant.now()), "失败应按指数退避推迟下次尝试");
    }
}
