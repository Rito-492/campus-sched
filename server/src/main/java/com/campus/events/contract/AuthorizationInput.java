package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record AuthorizationInput(
        @NotNull Identity identity,
        @NotNull @Valid List<Role> roles,
        @NotNull @Valid List<Permission> permissions,
        @NotNull @Valid List<ApprovalScope> approvalScopes,
        @NotNull @Valid List<UUID> managedResourceIds) {
}
