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
        assertEquals(new ActivityStateMachine.State(PENDING_APPROVER, 2, 2, 2), state);
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

        var approved = ActivityStateMachine.decide(ActivityStateMachine.submit(ActivityStateMachine.create()), 1, APPROVE);
        var approvedWithdrawal = ActivityStateMachine.withdraw(approved);
        assertEquals(new ActivityStateMachine.State(DRAFT, 2, 3, null), approvedWithdrawal);
        var resubmitted = ActivityStateMachine.submit(approvedWithdrawal);
        assertEquals(new ActivityStateMachine.State(PENDING_APPROVER, 2, 4, 2), resubmitted);
    }

    @Test
    void cancellationIsTerminalAndAnAlreadyCancelledStateIsIdempotent() {
        var cancelled = ActivityStateMachine.cancel(ActivityStateMachine.create());
        assertEquals(CANCELLED, cancelled.status());
        assertSame(cancelled, ActivityStateMachine.cancel(cancelled));
        assertThrows(ActivityStateMachine.InvalidTransitionException.class,
                () -> ActivityStateMachine.registrationChanged(cancelled));
        assertThrows(ActivityStateMachine.InvalidTransitionException.class,
                () -> ActivityStateMachine.publish(cancelled));
        assertThrows(ActivityStateMachine.InvalidTransitionException.class,
                () -> ActivityStateMachine.submit(cancelled));
        assertThrows(ActivityStateMachine.InvalidTransitionException.class,
                () -> ActivityStateMachine.edit(cancelled));
        assertThrows(ActivityStateMachine.InvalidTransitionException.class,
                () -> ActivityStateMachine.withdraw(cancelled));
        assertThrows(ActivityStateMachine.InvalidTransitionException.class,
                () -> ActivityStateMachine.decide(cancelled, 1, APPROVE));
        assertThrows(ActivityStateMachine.InvalidTransitionException.class,
                () -> ActivityStateMachine.resourceConfirmed(cancelled));
    }

    @Test
    void cancellationFromAnOpenActivityCannotBeReversed() {
        var open = new ActivityStateMachine.State(OPEN, 3, 8, null);
        var cancelled = ActivityStateMachine.cancel(open);
        assertEquals(new ActivityStateMachine.State(CANCELLED, 3, 9, null), cancelled);
        assertSame(cancelled, ActivityStateMachine.cancel(cancelled));
        assertThrows(ActivityStateMachine.InvalidTransitionException.class,
                () -> ActivityStateMachine.resourceConfirmed(cancelled));
    }

    @Test
    void everyLifecycleStatusCanCancelAndCancellationNeverChangesContentVersion() {
        for (var status : ActivityStateMachine.Status.values()) {
            var approvalVersion = status == PENDING_APPROVER ? 1 : null;
            var state = new ActivityStateMachine.State(status, 1, 7, approvalVersion);
            var cancelled = ActivityStateMachine.cancel(state);
            assertEquals(new ActivityStateMachine.State(CANCELLED, 1,
                    status == CANCELLED ? 7 : 8, null), cancelled, status.name());
        }
    }

    @Test
    void registrationMutationAdvancesLockVersionWithoutChangingContentVersion() {
        var open = new ActivityStateMachine.State(OPEN, 4, 9, null);
        assertEquals(new ActivityStateMachine.State(OPEN, 4, 10, null),
                ActivityStateMachine.registrationChanged(open));
    }
}
