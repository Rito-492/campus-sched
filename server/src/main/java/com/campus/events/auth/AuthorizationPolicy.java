package com.campus.events.auth;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import java.util.Set;
import java.util.UUID;

/** 重要写操作应先按UUID顺序锁定用户，再向本策略传入最新Actor。 */
public final class AuthorizationPolicy {
    private AuthorizationPolicy() {}
    public static void enabled(Actor actor) {
        if (actor == null || !actor.enabled()) throw ApiException.unauthenticated();
    }
    public static void role(Actor actor, Role role) {
        enabled(actor);
        if (!actor.roles().contains(role)) throw ApiException.forbidden();
    }
    public static void permission(Actor actor, Permission permission) {
        role(actor, Role.ADMIN);
        if (!actor.permissions().contains(permission)) throw ApiException.forbidden();
    }
    public static void approval(Actor actor, UUID ownerId, UUID organizationId, Set<String> categories) {
        role(actor, Role.APPROVER);
        if (actor.id().equals(ownerId) || categories.isEmpty()) throw ApiException.forbidden();
        var granted = actor.approvalScopes().stream().filter(s -> s.organizationId().equals(organizationId))
                .flatMap(s -> s.resourceCategories().stream()).collect(java.util.stream.Collectors.toSet());
        if (!granted.containsAll(categories)) throw ApiException.forbidden();
    }
    public static void resources(Actor actor, Permission permission, Set<UUID> resourceIds) {
        permission(actor, permission);
        if (resourceIds.isEmpty() || !actor.managedResourceIds().containsAll(resourceIds)) throw ApiException.forbidden();
    }
    public static void confirmation(Actor actor, UUID ownerId, Set<UUID> resourceIds) {
        enabled(actor);
        if (actor.id().equals(ownerId)) throw ApiException.forbidden();
        resources(actor, Permission.RESOURCE_CONFIRM, resourceIds);
    }
    public static void registration(Actor actor) {
        role(actor, Role.APPLICANT);
        if (actor.identity() != Identity.STUDENT) throw ApiException.forbidden();
    }
}
