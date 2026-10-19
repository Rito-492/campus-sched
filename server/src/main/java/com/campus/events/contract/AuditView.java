package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record AuditView(
        UUID id,
        UUID activityId,
        UUID actorId,
        String eventType,
        int versionNo,
        String summary,
        Instant createdAt) {
}
