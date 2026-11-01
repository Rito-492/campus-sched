package com.campus.events.notification;

import java.util.UUID;

/**
 * 通知投递探针：消费者在把某个 outbox 事件投递到某个收件人之前必须调用本方法。
 *
 * <p>生产默认实现 {@link NoopNotificationDeliveryProbe} 为空操作——真实投递即写入 notification 表，
 * 本探针不伪造任何成功、不提供公网故障接口（03 §2）。验收测试可用 {@code @Primary} 覆盖为「首次抛异常、
 * 随后放行」的探针，以验证消费者失败恢复与重试（D-AT-19）。
 */
public interface NotificationDeliveryProbe {
    void beforeDelivery(UUID eventId, UUID recipientId);
}
