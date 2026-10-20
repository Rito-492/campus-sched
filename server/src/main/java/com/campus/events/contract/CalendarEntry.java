package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record CalendarEntry(
        UUID resourceId,
        Instant startsAt,
        Instant endsAt,
        int quantity,
        UUID activityId,
        String label,
        Risk risk,
        CalendarSegment segment) {
}
