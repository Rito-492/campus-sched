package com.campus.events.resource;

import com.campus.events.auth.AuthorizationPolicy;
import com.campus.events.contract.*;
import com.campus.events.foundation.*;
import jakarta.validation.Validator;
import java.time.Duration;
import java.time.Instant;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ResourceService {
    private final ResourceRepository resources;
    private final AccountPort accounts;
    private final SchedulingPort scheduling;
    private final ObjectProvider<EventPort> events;
    private final CanonicalJson json;
    private final Validator validator;

    public ResourceService(ResourceRepository resources, AccountPort accounts, SchedulingPort scheduling,
                           ObjectProvider<EventPort> events, CanonicalJson json, Validator validator) {
        this.resources = resources; this.accounts = accounts; this.scheduling = scheduling;
        this.events = events; this.json = json; this.validator = validator;
    }

    public CommandResult list(Actor actor, ResourceKind kind, String category) {
        AuthorizationPolicy.enabled(actor);
        var rows = resources.list(kind, category);
        return Responses.list(rows.stream().limit(200).toList(), rows.size() > 200);
    }

    private void validate(Object input) {
        if (input == null || !validator.validate(input).isEmpty()) throw ApiException.invalid("资源参数不合法");
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ResourceView create(Actor actor, ResourceCreate input) {
        validate(input);
        Actor latest = accounts.lock(actor.id());
        AuthorizationPolicy.permission(latest, Permission.RESOURCE_MANAGE);
        ResourceView resource = resources.create(input);
        accounts.grantResource(latest.id(), resource.id());
        return resource;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public ResourceView eligibility(Actor actor, UUID resourceId, ResourceEligibility input) {
        validate(input);
        AuthorizationPolicy.resources(accounts.lock(actor.id()), Permission.RESOURCE_MANAGE, Set.of(resourceId));
        resources.lock(List.of(resourceId));
        var before = resources.get(resourceId);
        resources.eligibility(resourceId, actor.id(), input,
                json.write(new ResourceEligibility(before.allowTeacher(), before.allowStudent())), json.write(input));
        return resources.get(resourceId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public FaultView fault(Actor actor, UUID resourceId, FaultInput input) {
        validate(input);
        AuthorizationPolicy.resources(accounts.lock(actor.id()), Permission.RESOURCE_MANAGE, Set.of(resourceId));
        resources.lock(List.of(resourceId));
        if (input.unavailableQuantity() > resources.get(resourceId).quantity())
            throw ApiException.invalid("单条故障数量不能大于资源库存");
        // EventPort尚未交付时明确失败；事务不会提交无审计的故障。
        EventPort eventPort = events.getIfAvailable();
        if (eventPort == null) throw new ApiException(503, "INTERNAL_ERROR", "资源故障事件服务尚未就绪");
        var fault = resources.addFault(resourceId, actor.id(), input);
        var view = view(fault);
        if (view.affectedActivities().isEmpty())
            eventPort.append(new EventDraft(null, 0, actor.id(), "RESOURCE_FAULT",
                    "登记资源故障", List.of(), "资源故障", "资源存在不可用时段"));
        for (var affected : view.affectedActivities()) {
            var activity = resources.activity(affected.activityId());
            var recipients = new TreeSet<UUID>(Comparator.comparing(UUID::toString));
            recipients.add(activity.ownerId());
            recipients.addAll(activity.input().staffUserIds());
            recipients.addAll(resources.registered(activity.id()));
            eventPort.append(new EventDraft(activity.id(), affected.versionNo(), actor.id(), "RESOURCE_FAULT",
                    "活动预约资源出现故障", List.copyOf(recipients), "活动资源风险",
                    "活动预约资源出现故障，请查看资源风险并评估调整安排"));
        }
        return view;
    }

    public CommandResult faults(Actor actor, UUID resourceId, Instant from, Instant to) {
        range(from, to, false);
        AuthorizationPolicy.resources(accounts.get(actor.id()), Permission.RESOURCE_MANAGE, Set.of(resourceId));
        resources.get(resourceId);
        var rows = resources.listFaults(resourceId, from, to);
        return Responses.list(rows.stream().limit(200).map(this::view).toList(), rows.size() > 200);
    }

    private FaultView view(ResourceRepository.Fault fault) {
        var byActivity = new TreeMap<UUID, ResourceRepository.Occupancy>(Comparator.comparing(UUID::toString));
        resources.reservations(fault.resourceId(), fault.start(), fault.end(), null)
                .forEach(r -> byActivity.put(r.activityId(), r));
        var affected = byActivity.values().stream().map(r -> new AffectedActivity(r.activityId(),
                resources.version(r.activityId(), r.versionNo()).title(), r.versionNo(), scheduling.risk(r.activityId()))).toList();
        return new FaultView(fault.start(), fault.end(), fault.quantity(), fault.reason(), fault.id(), fault.resourceId(), affected);
    }

    public CommandResult calendar(Actor actor, Instant from, Instant to, UUID resourceId) {
        AuthorizationPolicy.enabled(actor);
        range(from, to, true);
        if (resourceId != null) resources.get(resourceId);
        var rows = resources.calendar(from, to, resourceId);
        var risks = new HashMap<UUID, Risk>();
        var visible = new HashMap<UUID, Boolean>();
        var entries = rows.stream().limit(200).map(row -> {
            if (row.segment() == CalendarSegment.FAULT)
                return new CalendarEntry(row.resourceId(), row.start(), row.end(), row.quantity(),
                        null, "资源不可用", Risk.AT_RISK, row.segment());
            boolean mayRead = visible.computeIfAbsent(row.activityId(), id -> canRead(actor, resources.activity(id)));
            return new CalendarEntry(row.resourceId(), row.start(), row.end(), row.quantity(),
                    mayRead ? row.activityId() : null,
                    mayRead ? resources.version(row.activityId(), row.version()).title() : "已占用",
                    risks.computeIfAbsent(row.activityId(), scheduling::risk), row.segment());
        }).toList();
        return Responses.list(entries, rows.size() > 200);
    }

    private boolean canRead(Actor actor, ResourceRepository.ActivityData activity) {
        if (actor.id().equals(activity.ownerId()) || activity.input().staffUserIds().contains(actor.id())) return true;
        var ids = new HashSet<UUID>();
        ids.add(activity.input().plan().venueId());
        activity.input().plan().equipment().forEach(e -> ids.add(e.resourceId()));
        if (actor.roles().contains(Role.ADMIN) && actor.managedResourceIds().containsAll(ids)
                && (actor.permissions().contains(Permission.RESOURCE_CONFIRM) || actor.permissions().contains(Permission.AUDIT_READ)))
            return true;
        var categories = new HashSet<String>();
        ids.forEach(id -> categories.add(resources.get(id).category()));
        try { AuthorizationPolicy.approval(actor, activity.ownerId(), activity.organizationId(), categories); return true; }
        catch (ApiException e) { return false; }
    }

    private static void range(Instant from, Instant to, boolean calendar) {
        if (from == null || to == null || !from.isBefore(to)
                || (calendar && Duration.between(from, to).compareTo(Duration.ofDays(7)) > 0))
            throw ApiException.invalid("查询区间必须递增；日历最多查询7天");
    }
}
