package com.campus.events.activity;

import org.junit.jupiter.api.Test;

import static com.campus.events.activity.ActivityStateMachine.Decision.APPROVE;
import static com.campus.events.activity.ActivityStateMachine.Decision.REJECT;
import static com.campus.events.activity.ActivityStateMachine.Status.*;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ActivityStateMachineTest {
    @Test
    void followsTheApprovedSchedulingAndPublishingPath() {
        var state = ActivityStateMachine.create();
        assertEquals(new ActivityStateMachine.State(DRAFT, 1, 0, null), state);
        state = ActivityStateMachine.edit(state);
        assertEquals(new ActivityStateMachine.State(DRAFT, 2, 1, null), state);
        state = ActivityStateMachine.submit(state);
        assertEquals(new ActivityStateMachine.State(PENDING_TEACHER, 2, 2, 2), state);
        state = ActivityStateMachine.decide(state, 2, APPROVE);
        assertEquals(new ActivityStateMachine.State(APPROVED_UNRESERVED, 2, 3, null), state);
        state = ActivityStateMachine.resourceConfirmed(state);
        assertEquals(new ActivityStateMachine.State(SCHEDULED, 2, 4, null), state);
        state = ActivityStateMachine.publish(state);
        assertEquals(new ActivityStateMachine.State(OPEN, 2, 5, null), state);
    }

    @Test
    void rejectsIllegalTransitionsAndStaleApprovalVersions() {
        var draft = ActivityStateMachine.create();
        assertThrows(ActivityStateMachine.InvalidTransitionException.class,
                () -> ActivityStateMachine.publish(draft));
        var pending = ActivityStateMachine.submit(draft);
        assertThrows(ActivityStateMachine.InvalidTransitionException.class,
                () -> ActivityStateMachine.decide(pending, 2, APPROVE));
        assertThrows(ActivityStateMachine.InvalidTransitionException.class,
                () -> ActivityStateMachine.edit(pending));
    }

    @Test
    void rejectionReturnsToDraftAndWithdrawalCreatesANewVersion() {
        var pending = ActivityStateMachine.submit(ActivityStateMachine.create());
        var rejected = ActivityStateMachine.decide(pending, 1, REJECT);
        assertEquals(new ActivityStateMachine.State(DRAFT, 1, 2, null), rejected);

        var withdrawn = ActivityStateMachine.withdraw(pending);
        assertEquals(new ActivityStateMachine.State(DRAFT, 2, 2, null), withdrawn);
        assertThrows(ActivityStateMachine.InvalidTransitionException.class,
                () -> ActivityStateMachine.decide(pending, 1, APPROVE));
    }

    @Test
    void cancellationIsTerminalAndAnAlreadyCancelledStateIsIdempotent() {
        var cancelled = ActivityStateMachine.cancel(ActivityStateMachine.create());
        assertEquals(CANCELLED, cancelled.status());
        assertSame(cancelled, ActivityStateMachine.cancel(cancelled));
        assertThrows(ActivityStateMachine.InvalidTransitionException.class,
                () -> ActivityStateMachine.registrationChanged(cancelled));
    }

    @Test
    void registrationMutationAdvancesLockVersionWithoutChangingContentVersion() {
        var open = new ActivityStateMachine.State(OPEN, 4, 9, null);
        assertEquals(new ActivityStateMachine.State(OPEN, 4, 10, null),
                ActivityStateMachine.registrationChanged(open));
    }
}
