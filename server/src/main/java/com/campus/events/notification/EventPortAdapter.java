package com.campus.events.notification;

import com.campus.events.contract.EventDraft;
import com.campus.events.contract.EventPort;
import com.campus.events.foundation.ApiException;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** EventPort 实现：同时追加 audit 与 outbox，过滤重复 recipientIds（02 §5）。只依赖本模块 Repository。 */
@Component
public class EventPortAdapter implements EventPort {
    private final EventRepository events;

    public EventPortAdapter(EventRepository events) {
        this.events = events;
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public UUID append(EventDraft event) {
        if (event == null || event.actorId() == null
                || event.eventType() == null || event.eventType().isBlank()
                || event.summary() == null || event.summary().isBlank()
                || event.title() == null || event.title().isBlank()
                || event.body() == null || event.body().isBlank()) {
            throw ApiException.invalid("事件字段不完整");
        }
        var recipients = event.recipientIds() == null ? List.<UUID>of()
                : event.recipientIds().stream().filter(Objects::nonNull).distinct()
                        .sorted(Comparator.comparing(UUID::toString)).toList();
        events.appendAudit(event);
        return events.appendOutbox(event, recipients);
    }
}
