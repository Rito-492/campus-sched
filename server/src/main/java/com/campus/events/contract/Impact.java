package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record Impact(
        Plan oldPlan,
        Plan newPlan,
        List<UUID> releasedResourceIds,
        List<UUID> acquiredResourceIds,
        List<UUID> retainedResourceIds,
        List<UUID> requiredStaffUserIds,
        List<UUID> registeredUserIds,
        int registeredCount,
        List<UUID> notificationUserIds,
        boolean approvalReset,
        Risk originalResourceRisk,
        List<String> changedFields) {
}
