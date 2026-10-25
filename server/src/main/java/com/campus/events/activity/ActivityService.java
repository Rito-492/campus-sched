package com.campus.events.activity;

import com.campus.events.auth.AuthorizationPolicy;
import com.campus.events.contract.*;
import com.campus.events.foundation.*;
import com.campus.events.registration.RegistrationRepository;
import com.campus.events.resource.ResourceRepository;
import jakarta.validation.Validator;
import java.time.Clock;
import java.util.*;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.*;

@Service
public class ActivityService {
    private final ActivityRepository activities;
    private final RegistrationRepository registrations;
    private final ActivityViews views;
    private final AccountPort accounts;
    private final SchedulingPort scheduling;
    private final ResourceRepository resources;
    private final ObjectProvider<EventPort> events;
    private final Validator validator;
    private final Clock clock;
    public ActivityService(ActivityRepository a,RegistrationRepository r,ActivityViews v,AccountPort accounts,
            SchedulingPort scheduling,ResourceRepository resources,ObjectProvider<EventPort> events,Validator validator,Clock clock) {
        activities=a; registrations=r; views=v; this.accounts=accounts; this.scheduling=scheduling;
        this.resources=resources; this.events=events; this.validator=validator; this.clock=clock;
    }
    private void validate(Object input) { if(input==null || !validator.validate(input).isEmpty()) throw ApiException.invalid("活动参数不合法"); }
    private Actor current(Actor actor) { AuthorizationPolicy.enabled(actor); var latest=accounts.get(actor.id()); AuthorizationPolicy.enabled(latest); return latest; }
    private Map<UUID,Actor> lockUsers(Collection<UUID> ids) {
        var locked=new HashMap<UUID,Actor>();
        ids.stream().distinct().sorted(Comparator.comparing(UUID::toString)).forEach(id->locked.put(id,accounts.lock(id)));
        return locked;
    }
    private Actor lockActor(ActivityRepository.Row row,Actor actor) {
        var users=lockUsers(List.of(row.ownerId(),actor.id()));
        AuthorizationPolicy.enabled(users.get(row.ownerId())); AuthorizationPolicy.enabled(users.get(actor.id()));
        return users.get(actor.id());
    }
    private Actor staff(Actor actor,ActivityInput input) {
        var ids=new ArrayList<>(input.staffUserIds()); ids.add(actor.id()); var locked=lockUsers(ids);
        var owner=locked.get(actor.id()); AuthorizationPolicy.role(owner,Role.APPLICANT);
        for(var id:input.staffUserIds()) {
            var user=locked.get(id);
            if(!user.enabled() || !owner.organizationId().equals(user.organizationId())) throw ApiException.invalid("工作人员须为同组织启用账号");
        }
        return owner;
    }
    private void owner(ActivityRepository.Row row,Actor actor) {
        AuthorizationPolicy.role(actor,Role.APPLICANT);
        if(!row.ownerId().equals(actor.id())) throw ApiException.forbidden();
    }
    private void version(ActivityRepository.Row row,int expected) {
        if(row.lockVersion()!=expected) throw new ApiException(409,"STALE_VERSION","活动已被其他操作修改");
    }
    private void future(ActivityRepository.Row row) {
        if(!clock.instant().isBefore(row.input().plan().startAt())) throw new ApiException(409,"INVALID_STATE","活动已经开始");
    }
    private ActivityStateMachine.State state(ActivityRepository.Row row) {
        return new ActivityStateMachine.State(ActivityStateMachine.Status.valueOf(row.status().name()),row.versionNo(),row.lockVersion(),
                row.status()==ActivityStatus.PENDING_APPROVER ? row.versionNo() : null);
    }
    private void transition(ActivityRepository.Row row,java.util.function.Function<ActivityStateMachine.State,ActivityStateMachine.State> action,Integer effective) {
        try {
            var next=action.apply(state(row)); activities.update(row,ActivityStatus.valueOf(next.status().name()),next.versionNo(),effective);
        } catch(ActivityStateMachine.InvalidTransitionException e) { throw new ApiException(409,"INVALID_STATE",e.getMessage()); }
    }
    private Set<UUID> resourceIds(ActivityInput input) {
        var ids=new HashSet<UUID>(); ids.add(input.plan().venueId()); input.plan().equipment().forEach(e->ids.add(e.resourceId())); return ids;
    }
    private Set<String> categories(ActivityInput input) {
        var categories=new HashSet<String>(); resourceIds(input).forEach(id->categories.add(resources.get(id).category())); return categories;
    }
    private boolean privileged(ActivityRepository.Row row,Actor actor) {
        if(row.ownerId().equals(actor.id())) return true;
        try { AuthorizationPolicy.approval(actor,row.ownerId(),row.organizationId(),categories(row.input())); return true; }
        catch(ApiException denied) { if(denied.status()!=403) throw denied; }
        try { AuthorizationPolicy.confirmation(actor,row.ownerId(),resourceIds(row.input())); return true; }
        catch(ApiException denied) { if(denied.status()!=403) throw denied; return false; }
    }
    private ActivityView http(ActivityRepository.Row row,Actor actor) { return views.http(row,actor,privileged(row,actor)); }
    private void event(ActivityRepository.Row row,Actor actor,String type,String summary,Collection<UUID> additional) {
        var port=events.getIfAvailable();
        if(port==null) throw new ApiException(503,"INTERNAL_ERROR","活动事件端口尚未就绪");
        var recipients=new TreeSet<UUID>(Comparator.comparing(UUID::toString));
        recipients.add(row.ownerId()); recipients.addAll(row.input().staffUserIds()); recipients.addAll(additional);
        port.append(new EventDraft(row.id(),row.versionNo(),actor.id(),type,summary,List.copyOf(recipients),row.input().title(),summary));
    }
    @Transactional(readOnly=true) public CommandResult list(Actor actor,String scope,ActivityStatus status) {
        actor=current(actor); var rows=activities.list(actor,scope,status); Actor latest=actor;
        return Responses.list(rows.stream().limit(200).map(row->http(row,latest)).toList(),rows.size()>200);
    }
    @Transactional(readOnly=true) public ActivityView get(Actor actor,UUID id) {
        actor=current(actor); var row=activities.get(id); boolean allowed=privileged(row,actor);
        if(!allowed && row.status()!=ActivityStatus.OPEN && !row.input().staffUserIds().contains(actor.id())) throw ApiException.forbidden();
        return views.http(row,actor,allowed);
    }
    @Transactional(propagation=Propagation.MANDATORY) public ActivityView create(Actor actor,ActivityInput input) {
        validate(input); var latest=staff(actor,input); UUID id=activities.create(latest,input); return http(activities.get(id),latest);
    }
    @Transactional(propagation=Propagation.MANDATORY) public ActivityView edit(Actor actor,UUID id,ActivityEdit command) {
        validate(command); var row=activities.lock(id); var latest=staff(actor,command.input()); owner(row,latest); version(row,command.expectedLockVersion());
        if(row.status()!=ActivityStatus.DRAFT) throw new ApiException(409,"INVALID_STATE","仅草稿可编辑");
        activities.version(id,row.versionNo()+1,command.input(),latest.id()); transition(row,ActivityStateMachine::edit,null);
        return http(activities.get(id),latest);
    }
    @Transactional(propagation=Propagation.MANDATORY) public ActivityView submit(Actor actor,UUID id,VersionCommand command) {
        validate(command); var row=activities.lock(id); var latest=staff(actor,row.input()); owner(row,latest); version(row,command.expectedLockVersion());
        if(row.status()!=ActivityStatus.DRAFT) throw new ApiException(409,"INVALID_STATE","仅草稿可提交");
        resources.lock(resourceIds(row.input()));
        // 检查资格与安排合法性；资源冲突留待最终联合确认，不在提交时占位。
        scheduling.check(row.input().plan(),row.input().expectedAttendees(),id,latest.identity());
        transition(row,ActivityStateMachine::submit,null);
        event(row,latest,"ACTIVITY_SUBMITTED","活动已提交审批",List.of());
        return http(activities.get(id),latest);
    }
    @Transactional(propagation=Propagation.MANDATORY) public ActivityView withdraw(Actor actor,UUID id,ReasonCommand command) {
        validate(command); var row=activities.lock(id); var latest=lockActor(row,actor); owner(row,latest); version(row,command.expectedLockVersion());
        if(row.status()!=ActivityStatus.PENDING_APPROVER && row.status()!=ActivityStatus.APPROVED_UNRESERVED)
            throw new ApiException(409,"INVALID_STATE","当前活动不可撤回");
        activities.version(id,row.versionNo()+1,row.input(),latest.id()); transition(row,ActivityStateMachine::withdraw,null);
        event(activities.get(id),latest,"ACTIVITY_WITHDRAWN",command.reason(),List.of()); return http(activities.get(id),latest);
    }
    @Transactional(propagation=Propagation.MANDATORY) public ActivityView decide(Actor actor,UUID id,ApprovalCommand command) {
        validate(command); var row=activities.lock(id); var latest=lockActor(row,actor);
        AuthorizationPolicy.approval(latest,row.ownerId(),row.organizationId(),categories(row.input())); version(row,command.expectedLockVersion());
        transition(row,s->ActivityStateMachine.decide(s,row.versionNo(),ActivityStateMachine.Decision.valueOf(command.decision().name())),null);
        activities.approval(row,latest.id(),command);
        event(row,latest,command.decision()==Decision.APPROVE?"ACTIVITY_APPROVED":"ACTIVITY_REJECTED",command.comment(),List.of());
        return http(activities.get(id),latest);
    }
    @Transactional(propagation=Propagation.MANDATORY) public ActivityView confirm(Actor actor,UUID id,VersionCommand command) {
        validate(command); var row=activities.lock(id); var latest=lockActor(row,actor);
        AuthorizationPolicy.confirmation(latest,row.ownerId(),resourceIds(row.input())); version(row,command.expectedLockVersion()); future(row);
        if(row.status()!=ActivityStatus.APPROVED_UNRESERVED) throw new ApiException(409,"INVALID_STATE","活动尚未通过业务审批");
        // 同一活动锁/用户锁之后，由统一端口锁资源、重查身份资格、全部预约。
        var check=scheduling.lockAndCheck(id,row.input().plan(),row.input().expectedAttendees(),accounts.get(row.ownerId()).identity());
        if(!check.feasible()) throw new ApiException(409,"RESOURCE_CONFLICT","资源联合确认失败",Map.of("conflicts",check.conflicts()));
        scheduling.replaceReservations(id,row.versionNo(),row.input().plan());
        transition(row,ActivityStateMachine::resourceConfirmed,row.versionNo());
        event(row,latest,"ACTIVITY_SCHEDULED","活动资源已确认",List.of()); return http(activities.get(id),latest);
    }
    @Transactional(propagation=Propagation.MANDATORY) public ActivityView publish(Actor actor,UUID id,VersionCommand command) {
        validate(command); var row=activities.lock(id); var latest=lockActor(row,actor); owner(row,latest); version(row,command.expectedLockVersion()); future(row);
        if(row.status()!=ActivityStatus.SCHEDULED) throw new ApiException(409,"INVALID_STATE","仅已预约活动可发布");
        if(views.changes().hasPending(id)) throw new ApiException(409,"CHANGE_PENDING","存在待处理改期");
        resources.lock(resourceIds(row.input()));
        if(scheduling.risk(id)!=Risk.NORMAL) throw new ApiException(409,"RESOURCE_CONFLICT","活动资源存在风险");
        transition(row,ActivityStateMachine::publish,row.effectiveVersionNo()); event(row,latest,"ACTIVITY_PUBLISHED","活动已发布",List.of());
        return http(activities.get(id),latest);
    }
    @Transactional(propagation=Propagation.MANDATORY) public ActivityView cancel(Actor actor,UUID id,ReasonCommand command) {
        validate(command); var row=activities.lock(id);
        // 归属先检查；重复取消不以旧lockVersion或开始时间阻断幂等读取。
        if(!row.ownerId().equals(actor.id())) throw ApiException.forbidden();
        if(row.status()==ActivityStatus.CANCELLED) { var latest=lockActor(row,actor); owner(row,latest); return http(row,latest); }
        version(row,command.expectedLockVersion()); future(row);
        var recipients=registrations.activeIds(id);
        // change行锁必须先于用户与资源锁；后续授权失败将整体回滚。
        views.changes().cancelPending(id,actor.id(),command.reason());
        var latest=lockActor(row,actor); owner(row,latest);
        scheduling.releaseReservations(id); registrations.cancel(id); transition(row,ActivityStateMachine::cancel,row.effectiveVersionNo());
        event(row,latest,"ACTIVITY_CANCELLED",command.reason(),recipients); return http(activities.get(id),latest);
    }
    @Transactional(propagation=Propagation.MANDATORY) public RegistrationView registration(Actor actor,UUID id,RegistrationCommand command) {
        validate(command); var row=activities.lock(id); var latest=lockActor(row,actor); AuthorizationPolicy.registration(latest); future(row);
        if(row.status()!=ActivityStatus.OPEN) throw new ApiException(409,"INVALID_STATE","活动未开放报名");
        if(!Objects.equals(row.effectiveVersionNo(),command.expectedVersionNo())) throw new ApiException(409,"STALE_VERSION","活动有效安排已变化");
        if(views.changes().hasPending(id)) throw new ApiException(409,"CHANGE_PENDING","改期期间冻结报名和退出");
        var previous=registrations.get(id,latest.id());
        RegistrationStatus target=command.action()==RegistrationAction.JOIN ? RegistrationStatus.ACTIVE : RegistrationStatus.WITHDRAWN;
        if(previous!=null && previous.status()==target) return previous;
        if(target==RegistrationStatus.ACTIVE && registrations.count(id)>=row.input().registrationCapacity())
            throw new ApiException(409,"CAPACITY_FULL","报名名额已满");
        if(target==RegistrationStatus.WITHDRAWN && (previous==null || previous.status()!=RegistrationStatus.ACTIVE))
            throw new ApiException(409,"INVALID_STATE","没有有效报名可退出");
        var result=registrations.save(id,latest.id(),target,row.effectiveVersionNo());
        transition(row,ActivityStateMachine::registrationChanged,row.effectiveVersionNo()); return result;
    }
    @Transactional(readOnly=true) public CommandResult registrations(Actor actor,UUID id) {
        actor=current(actor); var row=activities.get(id);
        if(!privileged(row,actor)) throw ApiException.forbidden();
        var rows=registrations.list(id); return Responses.list(rows.stream().limit(200).toList(),rows.size()>200);
    }
}
