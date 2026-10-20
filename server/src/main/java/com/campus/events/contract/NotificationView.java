package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record NotificationView(
        UUID id,
        UUID eventId,
        UUID activityId,
        Integer versionNo,
        String title,
        String body,
        Instant readAt,
        Instant createdAt) {
}
