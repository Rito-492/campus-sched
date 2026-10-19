package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record Conflict(
        ConflictCode code,
        UUID resourceId,
        String resourceName,
        Instant startsAt,
        Instant endsAt,
        Integer required,
        Integer available,
        String message) {
}
