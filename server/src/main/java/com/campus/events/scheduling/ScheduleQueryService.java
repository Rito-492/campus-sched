package com.campus.events.scheduling;

import com.campus.events.auth.AuthorizationPolicy;
import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.campus.events.resource.ResourceRepository;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;

/** 查询编排独立于SchedulingPort，避免ActivityPort→SchedulingPort循环依赖。 */
@Service
public class ScheduleQueryService {
    private final ObjectProvider<ActivityPort> activities;
    private final AccountPort accounts;
    private final ResourceRepository resources;
    private final SchedulingPort scheduling;
    public ScheduleQueryService(ObjectProvider<ActivityPort> activities, AccountPort accounts,
                                ResourceRepository resources, SchedulingPort scheduling) {
        this.activities = activities; this.accounts = accounts; this.resources = resources; this.scheduling = scheduling;
    }

    public Object preview(Actor actor, UUID activityId, Proposal proposal, boolean alternatives) {
        var port = activities.getIfAvailable();
        if (port == null) throw new ApiException(503, "INTERNAL_ERROR", "活动查询服务尚未就绪");
        var activity = port.get(activityId).activity();
        Actor latest = accounts.get(actor.id());
        AuthorizationPolicy.enabled(latest);
        if (!latest.id().equals(activity.ownerId())) {
            var categories = new HashSet<String>();
            categories.add(resources.get(proposal.targetPlan().venueId()).category());
            proposal.targetPlan().equipment().forEach(e -> categories.add(resources.get(e.resourceId()).category()));
            categories.add(resources.get(activity.input().plan().venueId()).category());
            activity.input().plan().equipment().forEach(e -> categories.add(resources.get(e.resourceId()).category()));
            AuthorizationPolicy.approval(latest, activity.ownerId(), activity.organizationId(), categories);
        }
        if (proposal.baseVersionNo() != activity.versionNo())
            throw new ApiException(409, "STALE_VERSION", "活动版本已变化",
                    Map.of("versionNo", activity.versionNo(), "lockVersion", activity.lockVersion()));
        Identity identity = accounts.get(activity.ownerId()).identity();
        return alternatives ? scheduling.alternatives(proposal.targetPlan(), activity.input().expectedAttendees(), activityId, identity)
                : scheduling.check(proposal.targetPlan(), activity.input().expectedAttendees(), activityId, identity);
    }
}
