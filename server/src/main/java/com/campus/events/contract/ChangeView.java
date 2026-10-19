package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ChangeView(
        UUID id,
        UUID activityId,
        int baseVersionNo,
        Plan targetPlan,
        String reason,
        ChangeStatus status,
        Impact impact,
        List<Confirmation> confirmations,
        DecisionRecord approverDecision,
        DecisionRecord resourceDecision,
        List<Conflict> lastConflicts,
        Integer appliedVersionNo,
        UUID createdBy,
        Instant createdAt,
        Instant updatedAt) {
}
