package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ReasonCommand(
        @Min(0) int expectedLockVersion,
        @NotNull @Size(max = 1000) @NotBlank String reason) {
}
