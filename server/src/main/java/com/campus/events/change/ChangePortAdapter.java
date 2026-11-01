package com.campus.events.change;

import com.campus.events.contract.ChangePort;
import java.util.UUID;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 只依赖本模块 Repository，不依赖 ActivityPort/ChangeService，避免改期↔活动循环（02 §5）。 */
@Component
public class ChangePortAdapter implements ChangePort {
    private final ChangeRepository changes;

    public ChangePortAdapter(ChangeRepository changes) {
        this.changes = changes;
    }

    @Override public UUID pendingId(UUID activityId) { return changes.pendingId(activityId); }

    @Override public boolean hasPending(UUID activityId) { return changes.hasPending(activityId); }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void cancelPending(UUID activityId, UUID actorId, String reason) {
        changes.cancelPending(activityId, actorId, reason);
    }
}
