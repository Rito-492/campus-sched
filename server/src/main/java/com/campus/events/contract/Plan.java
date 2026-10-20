package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record Plan(
        @NotNull Instant startAt,
        @NotNull Instant endAt,
        @Min(0) @Max(120) int setupMinutes,
        @Min(0) @Max(120) int teardownMinutes,
        @NotNull UUID venueId,
        @NotNull @Valid List<EquipmentNeed> equipment) {
    @AssertTrue(message = "活动须为分钟精度、30分钟至6小时，占用须在同一上海自然日，资源不得重复")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isValidPlan() {
        if (startAt == null || endAt == null || venueId == null || equipment == null) return false;
        long seconds = java.time.Duration.between(startAt, endAt).getSeconds();
        var zone = java.time.ZoneId.of("Asia/Shanghai");
        var ids = new java.util.HashSet<UUID>(); ids.add(venueId);
        return startAt.getNano() == 0 && endAt.getNano() == 0 && startAt.getEpochSecond() % 60 == 0
            && endAt.getEpochSecond() % 60 == 0 && seconds >= 1800 && seconds <= 21600
            && startAt.minusSeconds(setupMinutes * 60L).atZone(zone).toLocalDate()
                .equals(endAt.plusSeconds(teardownMinutes * 60L).atZone(zone).toLocalDate())
            && equipment.stream().allMatch(e -> e != null && ids.add(e.resourceId()));
    }

}
