package com.campus.events.scheduling;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.campus.events.resource.ResourceRepository;
import java.util.*;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

@Component
public class SchedulingPortAdapter implements SchedulingPort {
    private final ResourceRepository resources;
    private final ConstraintChecker checker;
    private final CandidateSearch candidates;
    private final Object checkedPlansKey = new Object();
    private record Checked(Plan plan, int attendees) {}

    public SchedulingPortAdapter(ResourceRepository resources, ConstraintChecker checker, CandidateSearch candidates) {
        this.resources = resources;
        this.checker = checker;
        this.candidates = candidates;
    }

    @Override public CheckResult check(Plan plan, int attendees, UUID excluded, Identity identity) {
        return checker.check(plan, attendees, excluded, identity);
    }

    @Override public AlternativesView alternatives(Plan plan, int attendees, UUID excluded, Identity identity) {
        var result = candidates.search(plan, attendees, excluded, identity, false);
        return new AlternativesView(result.items(), result.evaluated(), result.truncated(),
                CandidateRanking.OFFSETS_MINUTES, CandidateSearch.MAX_EVALUATIONS);
    }

    @SuppressWarnings("unchecked")
    private Map<UUID, Checked> checkedPlans() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive())
            throw new IllegalStateException("预约写入必须加入调用方事务");
        var plans = (Map<UUID, Checked>) TransactionSynchronizationManager.getResource(checkedPlansKey);
        if (plans == null) {
            plans = new HashMap<>();
            TransactionSynchronizationManager.bindResource(checkedPlansKey, plans);
            var bound = plans;
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void suspend() { TransactionSynchronizationManager.unbindResource(checkedPlansKey); }
                @Override public void resume() { TransactionSynchronizationManager.bindResource(checkedPlansKey, bound); }
                @Override public void afterCompletion(int status) { TransactionSynchronizationManager.unbindResourceIfPossible(checkedPlansKey); }
            });
        }
        return plans;
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public CheckResult lockAndCheck(UUID activityId, Plan target, int attendees, Identity identity) {
        var checked = checkedPlans();
        checked.remove(activityId);
        checker.validate(target, attendees, identity);
        var activity = resources.activity(activityId);
        if (activity.input().expectedAttendees() != attendees)
            throw ApiException.invalid("预计人数必须与持久化活动版本一致");
        var ids = new HashSet<>(ConstraintChecker.needs(target).keySet());
        resources.active(activityId).forEach(r -> ids.add(r.resourceId()));
        resources.lock(ids);
        // 上层已按activity→user→resource顺序持有活动及所有者锁；绝不信任传入的旧身份。
        var result = checker.check(target, attendees, activityId, resources.ownerIdentity(activityId));
        if (result.feasible()) checked.put(activityId, new Checked(target, attendees));
        return result;
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void replaceReservations(UUID activityId, int version, Plan target) {
        var checked = checkedPlans().remove(activityId);
        if (checked == null || !checked.plan().equals(target))
            throw new IllegalStateException("必须在同一事务中先成功检查同一计划");
        var persisted = resources.version(activityId, version);
        if (!persisted.plan().equals(target) || persisted.expectedAttendees() != checked.attendees())
            throw ApiException.invalid("预约计划必须与目标活动版本一致");
        var result = checker.check(target, checked.attendees(), activityId, resources.ownerIdentity(activityId));
        if (!result.feasible()) throw new ApiException(409, "RESOURCE_CONFLICT", "资源不可用", Map.of("conflicts", result.conflicts()));
        var interval = ConstraintChecker.interval(target);
        resources.release(activityId);
        ConstraintChecker.needs(target).forEach((id, quantity) ->
                resources.reserve(activityId, version, id, interval.start(), interval.end(), quantity));
    }

    @Override @Transactional(propagation = Propagation.MANDATORY)
    public void releaseReservations(UUID activityId) {
        checkedPlans().remove(activityId);
        resources.lock(resources.active(activityId).stream().map(ResourceRepository.Occupancy::resourceId).toList());
        resources.release(activityId);
    }

    @Override public Risk risk(UUID activityId) {
        for (var reservation : resources.active(activityId)) {
            if (!checker.capacity(resources.get(reservation.resourceId()),
                    new TimeInterval(reservation.start(), reservation.end()), reservation.quantity(), activityId).isEmpty())
                return Risk.AT_RISK;
        }
        return Risk.NORMAL;
    }
}
