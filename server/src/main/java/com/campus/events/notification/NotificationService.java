package com.campus.events.notification;

import com.campus.events.auth.AuthorizationPolicy;
import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.campus.events.foundation.Responses;
import com.campus.events.resource.ResourceRepository;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 通知与审计业务：API-34/35/36。通知仅本人可见；审计按所有者/现任审批与复核人/审计管理员放行。 */
@Service
public class NotificationService {
    private final NotificationRepository notifications;
    private final EventRepository events;
    private final ActivityPort activities;
    private final ResourceRepository resources;

    public NotificationService(NotificationRepository notifications, EventRepository events,
                               ActivityPort activities, ResourceRepository resources) {
        this.notifications = notifications;
        this.events = events;
        this.activities = activities;
        this.resources = resources;
    }

    @Transactional(readOnly = true)
    public CommandResult list(Actor actor, boolean unreadOnly) {
        AuthorizationPolicy.enabled(actor);
        var rows = notifications.list(actor.id(), unreadOnly, 201);
        return Responses.list(rows.stream().limit(200).toList(), rows.size() > 200);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public NotificationView read(Actor actor, UUID id) {
        AuthorizationPolicy.enabled(actor);
        notifications.get(id, actor.id()); // 仅本人记录可见，否则 404
        notifications.markRead(id, actor.id());
        return notifications.get(id, actor.id());
    }

    @Transactional(readOnly = true)
    public CommandResult audit(Actor actor, UUID activityId) {
        AuthorizationPolicy.enabled(actor);
        var activity = activities.get(activityId).activity();
        checkAudit(activity, actor);
        var rows = events.listAudit(activityId, 201);
        return Responses.list(rows.stream().limit(200).toList(), rows.size() > 200);
    }

    private void checkAudit(ActivityView activity, Actor actor) {
        if (activity.ownerId().equals(actor.id())) return;
        try {
            AuthorizationPolicy.approval(actor, activity.ownerId(), activity.organizationId(), categories(activity.input()));
            return;
        } catch (ApiException denied) { if (denied.status() != 403) throw denied; }
        try {
            AuthorizationPolicy.confirmation(actor, activity.ownerId(), resourceIds(activity.input()));
            return;
        } catch (ApiException denied) { if (denied.status() != 403) throw denied; }
        AuthorizationPolicy.permission(actor, Permission.AUDIT_READ);
        if (!actor.managedResourceIds().containsAll(resourceIds(activity.input()))) throw ApiException.forbidden();
    }

    private Set<UUID> resourceIds(ActivityInput input) {
        var ids = new HashSet<UUID>();
        ids.add(input.plan().venueId());
        input.plan().equipment().forEach(e -> ids.add(e.resourceId()));
        return ids;
    }

    private Set<String> categories(ActivityInput input) {
        var categories = new HashSet<String>();
        resourceIds(input).forEach(id -> categories.add(resources.get(id).category()));
        return categories;
    }
}
