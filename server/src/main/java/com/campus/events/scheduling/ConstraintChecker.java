package com.campus.events.scheduling;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.campus.events.resource.ResourceRepository;
import jakarta.validation.Validator;
import java.time.LocalTime;
import java.util.*;
import org.springframework.stereotype.Component;

@Component
public class ConstraintChecker {
    private final ResourceRepository resources;
    private final Validator validator;
    public ConstraintChecker(ResourceRepository resources, Validator validator) {
        this.resources = resources;
        this.validator = validator;
    }

    static boolean eligible(ResourceView resource, Identity identity) {
        return identity == Identity.TEACHER ? resource.allowTeacher() : resource.allowStudent();
    }

    public void validate(Plan plan, int attendees, Identity identity) {
        if (plan == null || identity == null || attendees < 1 || !validator.validate(plan).isEmpty())
            throw ApiException.invalid("计划、人数或申请人身份不合法");
    }

    static Map<UUID, Integer> needs(Plan plan) {
        var needs = new LinkedHashMap<UUID, Integer>();
        needs.put(plan.venueId(), 1);
        plan.equipment().forEach(e -> needs.put(e.resourceId(), e.quantity()));
        return needs;
    }

    static TimeInterval interval(Plan plan) {
        return TimeInterval.occupancy(plan.startAt(), plan.endAt(), plan.setupMinutes(), plan.teardownMinutes());
    }

    public CheckResult check(Plan plan, int attendees, UUID excluded, Identity identity) {
        validate(plan, attendees, identity);
        TimeInterval interval = interval(plan);
        Map<UUID, Integer> needs = needs(plan);
        var selected = needs.keySet().stream().map(resources::get).toList();
        for (ResourceView resource : selected) {
            boolean venue = resource.id().equals(plan.venueId());
            if ((venue ? ResourceKind.VENUE : ResourceKind.EQUIPMENT) != resource.kind())
                throw ApiException.invalid("计划中的资源类型不正确");
        }
        var restricted = selected.stream().filter(r -> !eligible(r, identity)).map(ResourceView::id).toList();
        if (!restricted.isEmpty())
            throw new ApiException(403, "RESOURCE_INELIGIBLE", "申请人身份不符合资源资格",
                    Map.of("resourceIds", restricted, "identity", identity));
        var conflicts = new ArrayList<Conflict>();
        for (ResourceView resource : selected) {
            if (!interval.withinOpeningHours(LocalTime.parse(resource.openFrom()), LocalTime.parse(resource.openTo())))
                conflicts.add(conflict(ConflictCode.OUTSIDE_HOURS, resource, interval, null, null, "占用超出资源开放时段"));
            if (resource.kind() == ResourceKind.VENUE && attendees > resource.capacity())
                conflicts.add(conflict(ConflictCode.VENUE_CAPACITY, resource, interval, attendees, resource.capacity(), "场地人数容量不足"));
            conflicts.addAll(capacity(resource, interval, needs.get(resource.id()), excluded));
        }
        return new CheckResult(conflicts.isEmpty(), List.copyOf(conflicts));
    }

    List<Conflict> capacity(ResourceView resource, TimeInterval interval, int quantity, UUID excluded) {
        var reservations = resources.reservations(resource.id(), interval.start(), interval.end(), excluded).stream()
                .map(r -> new CapacitySweep.Load(new TimeInterval(r.start(), r.end()), r.quantity())).toList();
        var faults = resources.faults(resource.id(), interval.start(), interval.end()).stream()
                .map(f -> new CapacitySweep.Load(new TimeInterval(f.start(), f.end()), f.quantity())).toList();
        return CapacitySweep.shortages(interval, quantity, resource.quantity(), reservations, faults).stream().map(s -> {
            ConflictCode code = s.faultQuantity() > 0 ? ConflictCode.RESOURCE_FAULT
                    : resource.kind() == ResourceKind.VENUE ? ConflictCode.VENUE_OCCUPIED : ConflictCode.EQUIPMENT_SHORTAGE;
            return conflict(code, resource, s.interval(), bounded(s.required()), bounded(s.available()),
                    s.faultQuantity() > 0 ? "故障后资源可用量不足" : "资源同时占用量超出可用量");
        }).toList();
    }

    private static int bounded(long value) { return (int) Math.min(Integer.MAX_VALUE, value); }
    private static Conflict conflict(ConflictCode code, ResourceView r, TimeInterval interval,
                                     Integer required, Integer available, String message) {
        return new Conflict(code, r.id(), r.name(), interval.start(), interval.end(), required, available, message);
    }
}
