package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ActivityInput(
        @NotNull @NotBlank @Size(max = 100) String title,
        @NotNull @Size(max = 2000) String description,
        @Min(1) int expectedAttendees,
        @Min(1) int registrationCapacity,
        @NotNull @Valid List<UUID> staffUserIds,
        @NotNull @Valid Plan plan) {
    @AssertTrue(message = "预计人数不得少于报名容量，工作人员不得重复")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isValidActivity() { return expectedAttendees >= registrationCapacity && staffUserIds != null
        && staffUserIds.stream().noneMatch(java.util.Objects::isNull) && staffUserIds.size() == new java.util.HashSet<>(staffUserIds).size(); }

}
