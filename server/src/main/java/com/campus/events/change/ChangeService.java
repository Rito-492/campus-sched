package com.campus.events.change;

import com.campus.events.auth.AuthorizationPolicy;
import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.campus.events.foundation.Responses;
import com.campus.events.resource.ResourceRepository;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/** 改期业务：API-26～33。统一锁序 receipt→activity→change→user→resource，单事务，无生产替身。 */
@Service
public class ChangeService {
    private final ChangeRepository changes;
    private final ActivityPort activities;
    private final AccountPort accounts;
    private final SchedulingPort scheduling;
    private final ResourceRepository resources;
    private final EventPort events;
    private final Validator validator;
    private final Clock clock;

    public ChangeService(ChangeRepository changes, ActivityPort activities, AccountPort accounts,
                         SchedulingPort scheduling, ResourceRepository resources, EventPort events,
                         Validator validator, Clock clock) {
        this.changes = changes;
        this.activities = activities;
        this.accounts = accounts;
        this.scheduling = scheduling;
        this.resources = resources;
        this.events = events;
        this.validator = validator;
        this.clock = clock;
    }

    // ------------------------------------------------------------------ 通用

    private void validate(Object input) { if (input == null || !validator.validate(input).isEmpty()) throw ApiException.invalid("改期参数不合法"); }

    private Actor current(Actor actor) {
        AuthorizationPolicy.enabled(actor);
        var latest = accounts.get(actor.id());
        AuthorizationPolicy.enabled(latest);
        return latest;
    }

    private Map<UUID, Actor> lockUsers(Collection<UUID> ids) {
        var locked = new HashMap<UUID, Actor>();
        ids.stream().distinct().sorted(Comparator.comparing(UUID::toString)).forEach(id -> locked.put(id, accounts.lock(id)));
        return locked;
    }

    private Set<UUID> resourceIds(Plan plan) {
        var ids = new HashSet<UUID>();
        ids.add(plan.venueId());
        plan.equipment().forEach(e -> ids.add(e.resourceId()));
        return ids;
    }

    private Set<String> categories(Plan plan) {
        var categories = new HashSet<String>();
        resourceIds(plan).forEach(id -> categories.add(resources.get(id).category()));
        return categories;
    }

    private void owner(ActivityView activity, Actor actor) {
        AuthorizationPolicy.role(actor, Role.APPLICANT);
        if (!activity.ownerId().equals(actor.id())) throw ApiException.forbidden();
    }

    private Actor lockOwner(ActivityView activity, Actor actor) {
        owner(activity, actor);
        var locked = accounts.lock(actor.id());
        AuthorizationPolicy.enabled(locked);
        return locked;
    }

    private void future(ActivityView activity) {
        if (!clock.instant().isBefore(activity.input().plan().startAt()))
            throw new ApiException(409, "INVALID_STATE", "活动已经开始");
    }

    private void targetFuture(Plan target) {
        if (!clock.instant().isBefore(target.startAt()))
            throw new ApiException(409, "INVALID_STATE", "改期目标必须在未来");
    }

    private record Locked(ActivitySnapshot snapshot, ChangeRepository.Row change) {}

    private Locked lockActivityThenChange(UUID changeId) {
        UUID activityId = changes.activityId(changeId);
        var snapshot = activities.lock(activityId);
        var change = changes.lock(changeId);
        return new Locked(snapshot, change);
    }

    private boolean privileged(ActivityView activity, Actor actor) {
        if (activity.ownerId().equals(actor.id())) return true;
        try { AuthorizationPolicy.approval(actor, activity.ownerId(), activity.organizationId(), categories(activity.input().plan())); return true; }
        catch (ApiException denied) { if (denied.status() != 403) throw denied; }
        try { AuthorizationPolicy.confirmation(actor, activity.ownerId(), resourceIds(activity.input().plan())); return true; }
        catch (ApiException denied) { if (denied.status() != 403) throw denied; return false; }
    }

    private void checkViewer(ActivityView activity, Actor actor) {
        if (activity.ownerId().equals(actor.id())
                || activity.input().staffUserIds().contains(actor.id())
                || privileged(activity, actor)) return;
        throw ApiException.forbidden();
    }

    private Impact impact(ActivityView activity, Plan target, List<UUID> registeredUserIds) {
        Plan old = activity.input().plan();
        var oldIds = resourceIds(old);
        var newIds = resourceIds(target);
        var released = oldIds.stream().filter(id -> !newIds.contains(id)).sorted(Comparator.comparing(UUID::toString)).toList();
        var acquired = newIds.stream().filter(id -> !oldIds.contains(id)).sorted(Comparator.comparing(UUID::toString)).toList();
        var retained = oldIds.stream().filter(newIds::contains).sorted(Comparator.comparing(UUID::toString)).toList();
        var staff = activity.input().staffUserIds().stream().sorted(Comparator.comparing(UUID::toString)).toList();
        var registered = registeredUserIds.stream().sorted(Comparator.comparing(UUID::toString)).toList();
        var recipients = new TreeSet<UUID>(Comparator.comparing(UUID::toString));
        recipients.add(activity.ownerId());
        recipients.addAll(staff);
        recipients.addAll(registered);
        return new Impact(old, target, released, acquired, retained, staff, registered,
                registered.size(), List.copyOf(recipients), true, scheduling.risk(activity.id()), changedFields(old, target));
    }

    private List<String> changedFields(Plan old, Plan target) {
        var changed = new ArrayList<String>();
        if (!old.startAt().equals(target.startAt())) changed.add("startAt");
        if (!old.endAt().equals(target.endAt())) changed.add("endAt");
        if (old.setupMinutes() != target.setupMinutes()) changed.add("setupMinutes");
        if (old.teardownMinutes() != target.teardownMinutes()) changed.add("teardownMinutes");
        if (!old.venueId().equals(target.venueId())) changed.add("venueId");
        if (!old.equipment().equals(target.equipment())) changed.add("equipment");
        return List.copyOf(changed);
    }

    private ChangeView view(ChangeRepository.Row row, boolean privileged) {
        var confirmations = changes.confirmations(row.id());
        Impact impact = row.impact();
        if (!privileged) {
            // 工作人员读取裁剪两个 ID 数组，但 registeredCount 保持真实（02 §2）。
            impact = new Impact(impact.oldPlan(), impact.newPlan(), impact.releasedResourceIds(),
                    impact.acquiredResourceIds(), impact.retainedResourceIds(), impact.requiredStaffUserIds(),
                    List.of(), impact.registeredCount(), List.of(), impact.approvalReset(),
                    impact.originalResourceRisk(), impact.changedFields());
        }
        return new ChangeView(row.id(), row.activityId(), row.baseVersionNo(), row.targetPlan(), row.reason(),
                row.status(), impact, confirmations, row.approverDecision(), row.resourceDecision(),
                row.lastConflicts(), row.appliedVersionNo(), row.createdBy(), row.createdAt(), row.updatedAt());
    }

    private void event(ActivityView activity, Actor actor, String type, String summary, int versionNo, List<UUID> recipients) {
        events.append(new EventDraft(activity.id(), versionNo, actor.id(), type, summary, recipients,
                activity.input().title(), summary));
    }

    // ------------------------------------------------------------------ API-26 预览

    @Transactional(readOnly = true)
    public ChangePreview preview(Actor actor, UUID activityId, Proposal proposal) {
        validate(proposal);
        actor = current(actor);
        var snapshot = activities.get(activityId);
        var activity = snapshot.activity();
        owner(activity, actor);
        if (proposal.baseVersionNo() != activity.versionNo())
            throw new ApiException(409, "STALE_VERSION", "活动版本已变化");
        future(activity);
        targetFuture(proposal.targetPlan());
        var identity = accounts.get(activity.ownerId()).identity();
        var check = scheduling.check(proposal.targetPlan(), activity.input().expectedAttendees(), activityId, identity);
        var alternatives = scheduling.alternatives(proposal.targetPlan(), activity.input().expectedAttendees(), activityId, identity);
        return new ChangePreview(check, alternatives,
                impact(activity, proposal.targetPlan(), snapshot.activeRegistrationUserIds()));
    }

    // ------------------------------------------------------------------ API-27 创建

    @Transactional(propagation = Propagation.MANDATORY)
    public ChangeView create(Actor actor, UUID activityId, ChangeCreate command) {
        validate(command);
        actor = current(actor);
        var snapshot = activities.lock(activityId);
        var activity = snapshot.activity();
        owner(activity, actor);
        if (activity.lockVersion() != command.expectedLockVersion())
            throw new ApiException(409, "STALE_VERSION", "活动已被其他操作修改");
        if (command.baseVersionNo() != activity.versionNo())
            throw new ApiException(409, "STALE_VERSION", "活动版本已变化");
        if (activity.status() != ActivityStatus.SCHEDULED && activity.status() != ActivityStatus.OPEN)
            throw new ApiException(409, "INVALID_STATE", "当前活动不可创建改期");
        future(activity);
        targetFuture(command.targetPlan());
        if (changes.hasPending(activityId))
            throw new ApiException(409, "CHANGE_PENDING", "活动已有待处理改期");
        var lockedOwner = lockOwner(activity, actor);
        // 创建即按所有者最新身份重新校验目标资格与安排合法性（01 §2）。
        scheduling.check(command.targetPlan(), activity.input().expectedAttendees(), activityId, lockedOwner.identity());
        var impact = impact(activity, command.targetPlan(), snapshot.activeRegistrationUserIds());
        var status = impact.requiredStaffUserIds().isEmpty() ? ChangeStatus.PENDING_APPROVER : ChangeStatus.AWAITING_CONFIRMATIONS;
        var row = changes.create(UUID.randomUUID(), activityId, command.baseVersionNo(), command.targetPlan(), impact,
                command.reason(), status, lockedOwner.id());
        changes.createConfirmations(row.id(), impact.requiredStaffUserIds());
        activityPortBump(activityId);
        event(activity, lockedOwner, "CHANGE_REQUESTED", "改期申请已提交", activity.versionNo(), impact.notificationUserIds());
        return view(changes.get(row.id()), true);
    }

    // ------------------------------------------------------------------ API-28/29 列表与详情

    @Transactional(readOnly = true)
    public CommandResult list(Actor actor, UUID activityId) {
        actor = current(actor);
        var activity = activities.get(activityId).activity();
        checkViewer(activity, actor);
        boolean privileged = privileged(activity, actor);
        var rows = changes.list(activityId);
        return Responses.list(rows.stream().limit(200).map(r -> view(r, privileged)).toList(), rows.size() > 200);
    }

    @Transactional(readOnly = true)
    public ChangeView get(Actor actor, UUID changeId) {
        actor = current(actor);
        var change = changes.get(changeId);
        var activity = activities.get(change.activityId()).activity();
        checkViewer(activity, actor);
        return view(change, privileged(activity, actor));
    }

    // ------------------------------------------------------------------ API-30 工作人员确认

    @Transactional(propagation = Propagation.MANDATORY)
    public ChangeView confirm(Actor actor, UUID changeId, ChangeDecision command) {
        validate(command);
        actor = current(actor);
        var locked = lockActivityThenChange(changeId);
        var activity = locked.snapshot().activity();
        var change = locked.change();
        if (change.status() != ChangeStatus.AWAITING_CONFIRMATIONS)
            throw new ApiException(409, "INVALID_STATE", "改期尚未进入人员确认");
        if (!change.impact().requiredStaffUserIds().contains(actor.id()))
            throw ApiException.forbidden();
        var staff = accounts.lock(actor.id());
        AuthorizationPolicy.enabled(staff);
        changes.decideConfirmation(changeId, staff.id(), command.decision(), command.comment(), clock.instant());
        if (command.decision() == Decision.REJECT) {
            changes.save(change, ChangeStatus.REJECTED, null, null, null, null);
            activityPortBump(activity.id());
            event(activity, staff, "CHANGE_REJECTED", "改期申请被工作人员拒绝", activity.versionNo(), change.impact().notificationUserIds());
        } else if (changes.allConfirmed(changeId)) {
            changes.save(change, ChangeStatus.PENDING_APPROVER, null, null, null, null);
            activityPortBump(activity.id());
            event(activity, staff, "CHANGE_CONFIRMED", "工作人员已确认改期", activity.versionNo(), change.impact().notificationUserIds());
        }
        // 工作人员非所有者/批准人/复核人，读取到的 ChangeView 需裁剪两个私密 ID 数组（02 §2）。
        return view(changes.get(changeId), privileged(activity, staff));
    }

    // ------------------------------------------------------------------ API-31 批准人审批

    @Transactional(propagation = Propagation.MANDATORY)
    public ChangeView approve(Actor actor, UUID changeId, ChangeDecision command) {
        validate(command);
        actor = current(actor);
        var locked = lockActivityThenChange(changeId);
        var activity = locked.snapshot().activity();
        var change = locked.change();
        if (change.status() != ChangeStatus.PENDING_APPROVER)
            throw new ApiException(409, "INVALID_STATE", "改期尚未进入审批");
        var users = lockUsers(List.of(actor.id(), activity.ownerId()));
        var operator = users.get(actor.id());
        AuthorizationPolicy.approval(operator, activity.ownerId(), activity.organizationId(), categories(change.targetPlan()));
        var record = new DecisionRecord(command.decision(), command.comment(), operator.id(), clock.instant());
        var status = command.decision() == Decision.APPROVE ? ChangeStatus.PENDING_RESOURCE : ChangeStatus.REJECTED;
        changes.save(change, status, record, change.resourceDecision(), change.lastConflicts(), null);
        activityPortBump(activity.id());
        event(activity, operator, command.decision() == Decision.APPROVE ? "CHANGE_APPROVED" : "CHANGE_REJECTED",
                command.decision() == Decision.APPROVE ? "改期已获批准" : "改期申请被批准人驳回",
                activity.versionNo(), change.impact().notificationUserIds());
        return view(changes.get(changeId), true);
    }

    // ------------------------------------------------------------------ API-32 资源复核（原子切换）

    @Transactional(propagation = Propagation.MANDATORY)
    public CommandResult resourceDecision(Actor actor, UUID changeId, ChangeDecision command) {
        validate(command);
        actor = current(actor);
        var locked = lockActivityThenChange(changeId);
        var activity = locked.snapshot().activity();
        var change = locked.change();
        if (change.status() != ChangeStatus.PENDING_RESOURCE)
            throw new ApiException(409, "INVALID_STATE", "改期尚未进入资源确认");
        if (change.baseVersionNo() != activity.versionNo())
            throw new ApiException(409, "STALE_VERSION", "活动版本已变化");
        future(activity);
        var users = lockUsers(List.of(actor.id(), activity.ownerId()));
        var operator = users.get(actor.id());
        AuthorizationPolicy.confirmation(operator, activity.ownerId(), resourceIds(change.targetPlan()));
        var ownerIdentity = users.get(activity.ownerId()).identity();

        if (command.decision() == Decision.REJECT) {
            var record = new DecisionRecord(Decision.REJECT, command.comment(), operator.id(), clock.instant());
            changes.save(change, ChangeStatus.REJECTED, change.approverDecision(), record, null, null);
            activityPortBump(activity.id());
            event(activity, operator, "CHANGE_REJECTED", "改期资源复核被驳回", activity.versionNo(), change.impact().notificationUserIds());
            return Responses.success(200, view(changes.get(changeId), true));
        }

        // 最终复核：改期目标此刻仍须在未来（01 §3）。预览/创建时目标可能在未来，但资源复核可能发生在目标已过去之后，
        // 若不重查会把有效版本与预约回写到过去；REJECT 分支不受此约束。
        targetFuture(change.targetPlan());

        var check = scheduling.lockAndCheck(activity.id(), change.targetPlan(), activity.input().expectedAttendees(), ownerIdentity);
        if (!check.feasible()) {
            var record = new DecisionRecord(Decision.APPROVE, command.comment(), operator.id(), clock.instant());
            changes.save(change, ChangeStatus.CONFLICTED, change.approverDecision(), record, check.conflicts(), null);
            activityPortBump(activity.id());
            event(activity, operator, "CHANGE_CONFLICTED", "改期资源冲突", activity.versionNo(), change.impact().notificationUserIds());
            var conflicted = view(changes.get(changeId), true);
            return Responses.error(409, "RESOURCE_CONFLICT", "资源联合确认失败",
                    Map.of("conflicts", check.conflicts(), "change", conflicted));
        }

        int version = activities.activatePlan(activity.id(), change.baseVersionNo(), change.targetPlan(), operator.id());
        scheduling.replaceReservations(activity.id(), version, change.targetPlan());
        var record = new DecisionRecord(Decision.APPROVE, command.comment(), operator.id(), clock.instant());
        changes.save(change, ChangeStatus.APPLIED, change.approverDecision(), record, null, version);
        event(activity, operator, "CHANGE_APPLIED", "改期已生效", version, change.impact().notificationUserIds());
        return Responses.success(200, view(changes.get(changeId), true));
    }

    // ------------------------------------------------------------------ API-33 撤回

    @Transactional(propagation = Propagation.MANDATORY)
    public ChangeView withdraw(Actor actor, UUID changeId, WithdrawInput command) {
        validate(command);
        actor = current(actor);
        var locked = lockActivityThenChange(changeId);
        var activity = locked.snapshot().activity();
        var change = locked.change();
        var owner = lockOwner(activity, actor);
        if (change.status() != ChangeStatus.AWAITING_CONFIRMATIONS && change.status() != ChangeStatus.PENDING_APPROVER
                && change.status() != ChangeStatus.PENDING_RESOURCE)
            throw new ApiException(409, "INVALID_STATE", "当前改期不可撤回");
        changes.save(change, ChangeStatus.WITHDRAWN, change.approverDecision(), change.resourceDecision(), change.lastConflicts(), null);
        activityPortBump(activity.id());
        event(activity, owner, "CHANGE_WITHDRAWN", "改期申请已撤回：" + command.reason(), activity.versionNo(), change.impact().notificationUserIds());
        return view(changes.get(changeId), true);
    }

    /** 已持有活动锁时递增 lockVersion（成功 activatePlan 已递增，不再重复调用）。 */
    private void activityPortBump(UUID activityId) { activities.bumpLockVersion(activityId); }
}
