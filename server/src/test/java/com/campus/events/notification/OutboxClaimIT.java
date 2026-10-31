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
 * 且每次认领生成唯一 claim_token：成功/失败记账只作用于仍持有本次令牌的 PENDING 事件，
 * 租约过期被重认领后旧认领者的晚到记账影响 0 行，不得篡改新认领者的状态与重试预算。
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

    private EventRepository.OutboxRow claim() {
        var batch = events.claimNext(20);
        assertEquals(1, batch.size(), "应恰好认领一条待处理事件");
        return batch.get(0);
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
        var row = claim();
        assertEquals(id, row.id());

        assertTrue(events.markDone(id, row.claimToken()));
        assertEquals("DONE", statusOf(id));
        assertEquals(1, attemptsOf(id));

        // 晚到的失败记账不得把 DONE 覆盖回 PENDING/FAILED，也不得重复递增 attempts。
        assertFalse(events.recordFailure(id, row.claimToken(), "late error"));
        assertEquals("DONE", statusOf(id));
        assertEquals(1, attemptsOf(id));
    }

    @Test void recordFailureBacksOffAndKeepsPendingForRetry() {
        UUID id = insertPending(0);
        var row = claim();
        assertEquals(id, row.id());

        assertTrue(events.recordFailure(id, row.claimToken(), "boom"));
        assertEquals("PENDING", statusOf(id));
        assertEquals(1, attemptsOf(id));
        assertTrue(nextAttemptOf(id).isAfter(Instant.now()), "失败应按指数退避推迟下次尝试");
    }

    /** 评审 P2 回归：A 旧租约 → B 重认领 → A 晚到失败 → B 成功，旧认领者不得修改 B 的状态/预算。 */
    @Test void staleClaimerCannotAffectReclaimedEvent() {
        UUID id = insertPending(4);   // 已有 4 次失败，第 5 次即达上限

        // A 认领（attempts=4 不变，仅领取租约与令牌）。
        var a = claim();
        assertEquals(id, a.id());
        UUID tokenA = a.claimToken();
        assertNotNull(tokenA, "认领应返回唯一令牌");

        // 模拟租约到期：把 next_attempt_at 拨回过去，允许 B 重认领同一事件。
        jdbc.update("UPDATE outbox_event SET next_attempt_at = now() - interval '1 second' WHERE id=?", id);

        // B 重认领，取得新的唯一令牌。
        var b = claim();
        assertEquals(id, b.id());
        UUID tokenB = b.claimToken();
        assertNotEquals(tokenA, tokenB, "重认领必须换发新令牌");

        // A 晚到的失败记账：令牌不匹配，影响 0 行，不得把 attempts 升到 5 或置 FAILED。
        assertFalse(events.recordFailure(id, tokenA, "stale error"), "旧认领者记账应失效");
        assertEquals("PENDING", statusOf(id), "旧认领者不得改变状态");
        assertEquals(4, attemptsOf(id), "旧认领者不得消耗重试预算");

        // B 投递成功：持有当前令牌，正常置 DONE，attempts 计入本次成功投递。
        assertTrue(events.markDone(id, tokenB), "当前认领者投递成功应生效");
        assertEquals("DONE", statusOf(id));
        assertEquals(5, attemptsOf(id));
    }
}
