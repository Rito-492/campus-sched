package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record EventDraft(
        UUID activityId,
        int versionNo,
        UUID actorId,
        String eventType,
        String summary,
        List<UUID> recipientIds,
        String title,
        String body) {
}
