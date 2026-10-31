package com.campus.events.notification;

import java.util.UUID;
import org.springframework.stereotype.Component;

/** 生产默认空实现：投递即写入 notification 表，本探针不改变任何行为。 */
@Component
public class NoopNotificationDeliveryProbe implements NotificationDeliveryProbe {
    @Override public void beforeDelivery(UUID eventId, UUID recipientId) {}
}
