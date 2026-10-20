package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record FaultInput(
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @Min(1) int unavailableQuantity,
        @NotNull @Size(max = 1000) @NotBlank String reason) {
    @AssertTrue(message = "故障开始时间必须早于结束时间")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isValidInterval() { return startAt != null && endAt != null && startAt.isBefore(endAt); }

}
