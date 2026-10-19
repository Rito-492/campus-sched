package com.campus.events.contract;

import java.time.Instant;
import java.util.List;
import java.util.UUID;
import jakarta.validation.Valid;
import jakarta.validation.constraints.*;

public record Actor(
        Identity identity,
        List<Role> roles,
        List<Permission> permissions,
        List<ApprovalScope> approvalScopes,
        List<UUID> managedResourceIds,
        UUID id,
        String displayName,
        UUID organizationId,
        boolean enabled) {
}
