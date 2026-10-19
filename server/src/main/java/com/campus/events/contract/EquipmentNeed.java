package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record EquipmentNeed(
        @NotNull UUID resourceId,
        @Min(1) int quantity) {
}
