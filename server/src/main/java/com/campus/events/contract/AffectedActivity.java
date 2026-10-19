package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record AffectedActivity(
        UUID activityId,
        String title,
        int versionNo,
        Risk resourceRisk) {
}
