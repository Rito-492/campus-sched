package com.campus.events.activity;

import java.util.Objects;

/** Pure activity lifecycle rules. Persistence, authorization and time checks belong to services. */
public final class ActivityStateMachine {
    private ActivityStateMachine() {}

    public enum Status {
        DRAFT,
        PENDING_TEACHER,
        APPROVED_UNRESERVED,
        SCHEDULED,
        OPEN,
        CANCELLED
    }

    public enum Decision { APPROVE, REJECT }

    /**
     * versionNo tracks immutable content versions; lockVersion tracks accepted state mutations.
     * approvalVersionNo is non-null only while a decision is pending.
     */
    public record State(Status status, int versionNo, int lockVersion, Integer approvalVersionNo) {
        public State {
            Objects.requireNonNull(status, "status");
            if (versionNo < 1 || lockVersion < 0) {
                throw new IllegalArgumentException("版本号无效");
            }
            if ((status == Status.PENDING_TEACHER) != (approvalVersionNo != null)) {
                throw new IllegalArgumentException("审批版本必须且只能在待审批状态存在");
            }
            if (approvalVersionNo != null && (approvalVersionNo < 1 || approvalVersionNo > versionNo)) {
                throw new IllegalArgumentException("审批版本无效");
            }
        }
    }

    public static State create() {
        return new State(Status.DRAFT, 1, 0, null);
    }

    /** A successful draft edit creates a new immutable content version. */
    public static State edit(State state) {
        requireStatus(state, Status.DRAFT);
        return next(state, Status.DRAFT, state.versionNo() + 1, null);
    }

    public static State submit(State state) {
        requireStatus(state, Status.DRAFT);
        return next(state, Status.PENDING_TEACHER, state.versionNo(), state.versionNo());
    }

    /** The decision must target the exact version frozen by submit. */
    public static State decide(State state, int decisionVersionNo, Decision decision) {
        requireStatus(state, Status.PENDING_TEACHER);
        Objects.requireNonNull(decision, "decision");
        if (decisionVersionNo != state.approvalVersionNo()) {
            throw new InvalidTransitionException("审批版本与当前待审版本不一致");
        }
        Status target = decision == Decision.APPROVE ? Status.APPROVED_UNRESERVED : Status.DRAFT;
        return next(state, target, state.versionNo(), null);
    }

    /** Withdrawal clones current content into a new draft version and invalidates the old approval. */
    public static State withdraw(State state) {
        if (state.status() != Status.PENDING_TEACHER && state.status() != Status.APPROVED_UNRESERVED) {
            throw invalid(state, "撤回");
        }
        return next(state, Status.DRAFT, state.versionNo() + 1, null);
    }

    public static State resourceConfirmed(State state) {
        requireStatus(state, Status.APPROVED_UNRESERVED);
        return next(state, Status.SCHEDULED, state.versionNo(), null);
    }

    public static State publish(State state) {
        requireStatus(state, Status.SCHEDULED);
        return next(state, Status.OPEN, state.versionNo(), null);
    }

    /** Caller must verify ownership and that the activity has not started. */
    public static State cancel(State state) {
        if (state.status() == Status.CANCELLED) {
            return state;
        }
        return next(state, Status.CANCELLED, state.versionNo(), null);
    }

    /** A real JOIN/WITHDRAW mutation changes the lock sequence, not content version. */
    public static State registrationChanged(State state) {
        if (state.status() != Status.OPEN) {
            throw invalid(state, "报名变更");
        }
        return next(state, state.status(), state.versionNo(), null);
    }

    private static State next(State previous, Status status, int versionNo, Integer approvalVersionNo) {
        return new State(status, versionNo, previous.lockVersion() + 1, approvalVersionNo);
    }

    private static void requireStatus(State state, Status expected) {
        Objects.requireNonNull(state, "state");
        if (state.status() != expected) {
            throw invalid(state, "状态转换");
        }
    }

    private static InvalidTransitionException invalid(State state, String operation) {
        return new InvalidTransitionException(operation + "不允许作用于状态 " + state.status());
    }

    public static final class InvalidTransitionException extends IllegalStateException {
        public InvalidTransitionException(String message) { super(message); }
    }
}
