package com.campus.events.activity;

import com.campus.events.contract.*;
import com.campus.events.foundation.*;
import com.campus.events.registration.RegistrationRepository;
import com.campus.events.resource.ResourceRepository;
import jakarta.validation.Validation;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class ActivityServiceTest {
    final UUID id=UUID.randomUUID(),owner=UUID.randomUUID(),student=UUID.randomUUID(),venue=UUID.randomUUID(),org=UUID.randomUUID();
    final Instant now=Instant.parse("2030-01-01T00:00:00Z");
    final Plan plan=new Plan(now.plusSeconds(3600),now.plusSeconds(7200),0,0,venue,List.of());
    ActivityRepository activities; RegistrationRepository registrations; AccountPort accounts; SchedulingPort scheduling;
    ChangePort changes; EventPort events; ResourceRepository resources; ActivityService service; ActivityViews views;
    static final jakarta.validation.ValidatorFactory VALIDATION=Validation.buildDefaultValidatorFactory();
    @AfterAll static void closeValidator() { VALIDATION.close(); }
    Actor applicant(UUID user,Identity identity) { return new Actor(identity,List.of(Role.APPLICANT),List.of(),List.of(),List.of(),user,"账号",org,true); }
    Actor approver(UUID user,List<String> categories) { return new Actor(Identity.TEACHER,List.of(Role.APPLICANT,Role.APPROVER),List.of(),List.of(new ApprovalScope(org,categories)),List.of(),user,"批准人",org,true); }
    Actor admin(UUID user,List<Permission> permissions) { return new Actor(Identity.TEACHER,List.of(Role.ADMIN),permissions,List.of(),List.of(venue),user,"管理员",org,true); }
    ActivityRepository.Row row(ActivityStatus status) { return new ActivityRepository.Row(id,org,owner,status,1,status==ActivityStatus.OPEN||status==ActivityStatus.SCHEDULED?1:null,3,
            new ActivityInput("测试活动","",10,1,List.of(owner),plan),now); }
    @BeforeEach void setup() {
        activities=mock(ActivityRepository.class); registrations=mock(RegistrationRepository.class); accounts=mock(AccountPort.class);
        scheduling=mock(SchedulingPort.class); changes=mock(ChangePort.class); events=mock(EventPort.class); resources=mock(ResourceRepository.class);
        var beans=new StaticListableBeanFactory(); beans.addBean("changes",changes); beans.addBean("events",events);
        views=new ActivityViews(registrations,beans.getBeanProvider(ChangePort.class),scheduling);
        service=new ActivityService(activities,registrations,views,accounts,scheduling,resources,beans.getBeanProvider(EventPort.class),VALIDATION.getValidator(),Clock.fixed(now,ZoneOffset.UTC));
        when(accounts.lock(owner)).thenReturn(applicant(owner,Identity.TEACHER)); when(accounts.get(owner)).thenReturn(applicant(owner,Identity.TEACHER));
        when(accounts.lock(student)).thenReturn(applicant(student,Identity.STUDENT)); when(accounts.get(student)).thenReturn(applicant(student,Identity.STUDENT));
        when(activities.lock(id)).thenReturn(row(ActivityStatus.OPEN)); when(activities.get(id)).thenReturn(row(ActivityStatus.OPEN));
        when(scheduling.risk(id)).thenReturn(Risk.NORMAL); when(registrations.activeIds(id)).thenReturn(List.of());
        when(resources.get(venue)).thenReturn(new ResourceView(true,true,ResourceKind.VENUE,"场地","VENUE",20,1,"00:00","23:59",venue,now));
    }
    ApiException error(Runnable action,String code) { var ex=assertThrows(ApiException.class,action::run); assertEquals(code,ex.code()); return ex; }
    @Test void oldContentVersionAndFrozenChangesRejectRegistration() {
        error(()->service.registration(applicant(student,Identity.STUDENT),id,new RegistrationCommand(RegistrationAction.JOIN,2)),"STALE_VERSION");
        when(changes.hasPending(id)).thenReturn(true);
        error(()->service.registration(applicant(student,Identity.STUDENT),id,new RegistrationCommand(RegistrationAction.WITHDRAW,1)),"CHANGE_PENDING");
        verify(registrations,never()).save(any(),any(),any(),anyInt());
    }
    @Test void registrationUsesCurrentIdentityRatherThanRequestActor() {
        when(accounts.lock(student)).thenReturn(applicant(student,Identity.TEACHER));
        error(()->service.registration(applicant(student,Identity.STUDENT),id,new RegistrationCommand(RegistrationAction.JOIN,1)),"FORBIDDEN");
    }
    @Test void fullCapacityRejectsButDuplicateJoinReturnsExistingRow() {
        when(registrations.count(id)).thenReturn(1);
        error(()->service.registration(applicant(student,Identity.STUDENT),id,new RegistrationCommand(RegistrationAction.JOIN,1)),"CAPACITY_FULL");
        var previous=new RegistrationView(id,student,RegistrationStatus.ACTIVE,1); when(registrations.get(id,student)).thenReturn(previous);
        assertSame(previous,service.registration(applicant(student,Identity.STUDENT),id,new RegistrationCommand(RegistrationAction.JOIN,1)));
        verify(registrations,never()).save(any(),any(),any(),anyInt());
    }
    @Test void withdrawRequiresPriorRegistrationAndDuplicateWithdrawDoesNotBumpVersion() {
        error(()->service.registration(applicant(student,Identity.STUDENT),id,new RegistrationCommand(RegistrationAction.WITHDRAW,1)),"INVALID_STATE");
        var previous=new RegistrationView(id,student,RegistrationStatus.WITHDRAWN,1); when(registrations.get(id,student)).thenReturn(previous);
        assertSame(previous,service.registration(applicant(student,Identity.STUDENT),id,new RegistrationCommand(RegistrationAction.WITHDRAW,1)));
        verify(activities,never()).update(any(),any(),anyInt(),any());
    }
    @Test void approvalRejectsTeacherWithoutRoleAndIncompleteScopeAndSelfApproval() {
        when(activities.lock(id)).thenReturn(row(ActivityStatus.PENDING_APPROVER));
        error(()->service.decide(applicant(owner,Identity.TEACHER),id,new ApprovalCommand(3,Decision.APPROVE,"")),"FORBIDDEN");
        when(accounts.lock(student)).thenReturn(approver(student,List.of("PROJECTOR")));
        error(()->service.decide(approver(student,List.of("VENUE")),id,new ApprovalCommand(3,Decision.APPROVE,"")),"FORBIDDEN");
        when(accounts.lock(owner)).thenReturn(approver(owner,List.of("VENUE")));
        error(()->service.decide(approver(owner,List.of("VENUE")),id,new ApprovalCommand(3,Decision.APPROVE,"")),"FORBIDDEN");
        verifyNoInteractions(scheduling);
    }
    @Test void resourceConfirmationRequiresSpecificPermissionAndAvoidsOwner() {
        when(activities.lock(id)).thenReturn(row(ActivityStatus.APPROVED_UNRESERVED));
        when(accounts.lock(student)).thenReturn(admin(student,List.of(Permission.USER_MANAGE)));
        error(()->service.confirm(admin(student,List.of(Permission.RESOURCE_CONFIRM)),id,new VersionCommand(3)),"FORBIDDEN");
        when(accounts.lock(owner)).thenReturn(admin(owner,List.of(Permission.RESOURCE_CONFIRM)));
        error(()->service.confirm(admin(owner,List.of(Permission.RESOURCE_CONFIRM)),id,new VersionCommand(3)),"FORBIDDEN");
        verifyNoInteractions(scheduling);
    }
    @Test void staleDraftEditCannotAppendAVersion() {
        when(activities.lock(id)).thenReturn(row(ActivityStatus.DRAFT));
        error(()->service.edit(applicant(owner,Identity.TEACHER),id,new ActivityEdit(2,row(ActivityStatus.DRAFT).input())),"STALE_VERSION");
        verify(activities,never()).version(any(),anyInt(),any(),any());
    }
    @Test void publicDetailsHideStaffButInternalSnapshotRetainsThemAndNullsMyStatus() {
        var external=service.get(applicant(student,Identity.STUDENT),id);
        assertEquals(List.of(),external.input().staffUserIds());
        var internal=views.snapshot(row(ActivityStatus.OPEN));
        assertEquals(List.of(owner),internal.activity().input().staffUserIds()); assertNull(internal.activity().myRegistrationStatus());
        error(()->service.registrations(applicant(student,Identity.STUDENT),id),"FORBIDDEN");
    }
    @Test void cancelledActivityCannotBeJoinedAndOtherOwnerCannotReadIdempotentCancellation() {
        when(activities.lock(id)).thenReturn(row(ActivityStatus.CANCELLED));
        error(()->service.registration(applicant(student,Identity.STUDENT),id,new RegistrationCommand(RegistrationAction.JOIN,1)),"INVALID_STATE");
        error(()->service.cancel(applicant(student,Identity.STUDENT),id,new ReasonCommand(0,"取消")),"FORBIDDEN");
        assertEquals(ActivityStatus.CANCELLED,service.cancel(applicant(owner,Identity.TEACHER),id,new ReasonCommand(0,"重复")).status());
        verify(changes,never()).cancelPending(any(),any(),any());
    }
    @Test void missingChangePortCannotMasqueradeAsNoPendingChange() {
        var empty=new StaticListableBeanFactory(); var missing=new ActivityViews(registrations,empty.getBeanProvider(ChangePort.class),scheduling);
        assertEquals(503,assertThrows(ApiException.class,()->missing.snapshot(row(ActivityStatus.OPEN))).status());
        assertNull(missing.snapshot(row(ActivityStatus.DRAFT)).activity().activeChangeId());
    }
    @Test void adapterCopiesMetadataAndAdvancesOnlyActiveRegistrationVersions() {
        var adapter=new ActivityPortAdapter(activities,registrations,views,VALIDATION.getValidator());
        var target=new Plan(plan.startAt().plusSeconds(3600),plan.endAt().plusSeconds(3600),0,0,venue,List.of());
        assertEquals(2,adapter.activatePlan(id,1,target,owner));
        verify(activities).requireLock(id);
        verify(activities).version(eq(id),eq(2),eq(new ActivityInput("测试活动","",10,1,List.of(owner),target)),eq(owner));
        verify(registrations).activate(id,2); verifyNoInteractions(events);
        error(()->adapter.activatePlan(id,2,target,owner),"STALE_VERSION");
    }
    @Test void mineScopeRequiresCurrentApplicantRole() {
        when(accounts.get(student)).thenReturn(admin(student,List.of(Permission.USER_MANAGE)));
        error(()->service.list(applicant(student,Identity.STUDENT),"mine",null),"FORBIDDEN");
        verify(activities,never()).list(any(),any(),any());
    }
    @Test void pastRegistrationAllowsCancelledDetailsButNotParticipantRoster() {
        var cancelled=row(ActivityStatus.CANCELLED);
        // 与当前工作人员无关：报名历史本身就是详情读取依据。
        cancelled=new ActivityRepository.Row(id,org,owner,cancelled.status(),1,1,3,
                new ActivityInput("测试活动","",10,1,List.of(),plan),now);
        when(activities.get(id)).thenReturn(cancelled);
        when(registrations.get(id,student)).thenReturn(new RegistrationView(id,student,RegistrationStatus.CANCELLED,1));
        assertEquals(ActivityStatus.CANCELLED,service.get(applicant(student,Identity.STUDENT),id).status());
        error(()->service.registrations(applicant(student,Identity.STUDENT),id),"FORBIDDEN");
    }
    @Test void publishRejectsStartedActivityWithoutStateOrEventWrites() {
        var past=new Plan(now.minusSeconds(3600),now,0,0,venue,List.of());
        var scheduled=row(ActivityStatus.SCHEDULED);
        when(activities.lock(id)).thenReturn(new ActivityRepository.Row(id,org,owner,ActivityStatus.SCHEDULED,1,1,3,
                new ActivityInput("已开始","",10,1,List.of(),past),scheduled.createdAt()));
        var ex=error(()->service.publish(applicant(owner,Identity.TEACHER),id,new VersionCommand(3)),"INVALID_STATE");
        assertEquals(409,ex.status()); assertEquals("活动已经开始",ex.getMessage());
        verify(activities,never()).update(any(),any(),anyInt(),any()); verifyNoInteractions(events);
    }
    @Test void publishRejectsWrongServiceStateWithoutStateOrEventWrites() {
        when(activities.lock(id)).thenReturn(row(ActivityStatus.DRAFT));
        assertEquals(409,error(()->service.publish(applicant(owner,Identity.TEACHER),id,new VersionCommand(3)),"INVALID_STATE").status());
        verify(activities,never()).update(any(),any(),anyInt(),any()); verifyNoInteractions(events);
    }
    @Test void publishRejectsCurrentResourceRiskWithoutStateOrEventWrites() {
        when(activities.lock(id)).thenReturn(row(ActivityStatus.SCHEDULED)); when(scheduling.risk(id)).thenReturn(Risk.AT_RISK);
        var ex=error(()->service.publish(applicant(owner,Identity.TEACHER),id,new VersionCommand(3)),"RESOURCE_CONFLICT");
        assertEquals(409,ex.status()); assertEquals("活动资源存在风险",ex.getMessage());
        verify(activities,never()).update(any(),any(),anyInt(),any()); verifyNoInteractions(events);
    }
    @Test void pendingChangeDoesNotAddAnUndocumentedPublishRestriction() {
        when(activities.lock(id)).thenReturn(row(ActivityStatus.SCHEDULED));
        when(changes.hasPending(id)).thenReturn(true);
        service.publish(applicant(owner,Identity.TEACHER),id,new VersionCommand(3));
        verify(activities).update(any(),eq(ActivityStatus.OPEN),eq(1),eq(1));
    }
    @Test void nonOwnerEditAndSubmitRejectBeforeLockingSuppliedStaff() {
        var arbitraryStaff=UUID.randomUUID(); when(activities.lock(id)).thenReturn(row(ActivityStatus.DRAFT));
        var input=new ActivityInput("测试活动","",10,1,List.of(arbitraryStaff),plan);
        error(()->service.edit(applicant(student,Identity.STUDENT),id,new ActivityEdit(3,input)),"FORBIDDEN");
        error(()->service.submit(applicant(student,Identity.STUDENT),id,new VersionCommand(3)),"FORBIDDEN");
        verify(accounts,never()).lock(any());
    }
}
