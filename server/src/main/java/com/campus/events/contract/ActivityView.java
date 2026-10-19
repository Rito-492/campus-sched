package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ActivityView(
        UUID id,
        UUID organizationId,
        UUID ownerId,
        ActivityStatus status,
        int versionNo,
        Integer effectiveVersionNo,
        int lockVersion,
        ActivityInput input,
        int registrationCount,
        Risk resourceRisk,
        UUID activeChangeId,
        RegistrationStatus myRegistrationStatus,
        Instant createdAt) {
}
