package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record ApprovalCommand(
        @Min(0) int expectedLockVersion,
        @NotNull Decision decision,
        @NotNull @Size(max = 1000) String comment) {
    @AssertTrue(message = "拒绝必须填写意见")
    @com.fasterxml.jackson.annotation.JsonIgnore
    public boolean isValidDecision() { return decision != Decision.REJECT || comment != null && !comment.isBlank(); }

}
