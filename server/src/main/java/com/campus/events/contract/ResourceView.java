package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ResourceView(
        boolean allowTeacher,
        boolean allowStudent,
        ResourceKind kind,
        String name,
        String category,
        Integer capacity,
        int quantity,
        String openFrom,
        String openTo,
        UUID id,
        Instant createdAt) {
}
