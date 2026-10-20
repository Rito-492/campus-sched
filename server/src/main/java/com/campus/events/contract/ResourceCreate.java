package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ResourceCreate(
        boolean allowTeacher,
        boolean allowStudent,
        @NotNull ResourceKind kind,
        @NotNull @NotBlank String name,
        @NotNull @NotBlank String category,
        Integer capacity,
        @Min(1) int quantity,
        @NotNull @NotBlank @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]") String openFrom,
        @NotNull @NotBlank @Pattern(regexp = "(?:[01][0-9]|2[0-3]):[0-5][0-9]") String openTo) {
    @AssertTrue(message = "资源容量、数量、类别或开放区间不合法")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isValidResource() {
        try {
            return java.time.LocalTime.parse(openFrom).isBefore(java.time.LocalTime.parse(openTo))
                && (kind == ResourceKind.VENUE && capacity != null && capacity > 0 && quantity == 1 && "VENUE".equals(category)
                    || kind == ResourceKind.EQUIPMENT && capacity == null && quantity > 0);
        } catch (RuntimeException ex) { return false; }
    }

}
