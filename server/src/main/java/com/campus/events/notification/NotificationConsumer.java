package com.campus.events.notification;

import java.util.List;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

/**
 * 通知消费者：每 2s 认领一批到期 outbox 事件，独立事务投递通知并记录成败（02 §5）。
 * 认领、投递、失败记账分属三个事务，投递失败不回滚已提交业务，仅按指数退避重试。
 */
@Component
public class NotificationConsumer {
    private static final int BATCH_SIZE = 20;

    private final EventRepository events;
    private final NotificationRepository notifications;
    private final NotificationDeliveryProbe probe;
    private final TransactionTemplate transactions;

    public NotificationConsumer(EventRepository events, NotificationRepository notifications,
                                NotificationDeliveryProbe probe, TransactionTemplate transactions) {
        this.events = events;
        this.notifications = notifications;
        this.probe = probe;
        this.transactions = transactions;
    }

    @Scheduled(fixedDelay = 2000)
    public void drain() {
        List<EventRepository.OutboxRow> batch = transactions.execute(tx -> events.claimNext(BATCH_SIZE));
        if (batch == null || batch.isEmpty()) return;
        for (var row : batch) {
            try {
                transactions.executeWithoutResult(tx -> { deliver(row); });
            } catch (ClaimLostException lost) {
                // 租约过期已被他人重认领：本次认领作废，不记账、不重试，交由新认领者完成。
            } catch (RuntimeException ex) {
                transactions.executeWithoutResult(tx -> {
                    // 返回 false 表示记账时也已丢失所有权：静默放弃，不影响新认领者。
                    events.recordFailure(row.id(), row.claimToken(), ex.getMessage());
                });
            }
        }
    }

    private void deliver(EventRepository.OutboxRow row) {
        for (var recipient : row.payload().recipientIds()) {
            probe.beforeDelivery(row.id(), recipient);
            notifications.insert(row.id(), recipient, row.activityId(), row.versionNo(),
                    row.payload().title(), row.payload().body());
        }
        // markDone 若影响 0 行说明所有权已转移：本次投递不得作为成功提交，抛异常回滚通知写入。
        if (!events.markDone(row.id(), row.claimToken())) {
            throw ClaimLostException.INSTANCE;
        }
    }

    /** 认领所有权已转移给他人：用于回滚本次投递并静默放弃，不当作投递失败重试。 */
    private static final class ClaimLostException extends RuntimeException {
        static final ClaimLostException INSTANCE = new ClaimLostException();
    }
}
