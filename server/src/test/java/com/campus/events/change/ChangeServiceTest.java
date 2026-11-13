package com.campus.events.change;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.campus.events.resource.ResourceRepository;
import jakarta.validation.Validation;
import java.time.*;
import java.util.*;
import org.junit.jupiter.api.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import org.mockito.ArgumentCaptor;

/**
 * 改期资源复核「目标仍须在未来」回归（评审 P1）。
 *
 * <p>场景：原活动仍在未来（future(activity) 通过），但改期目标在创建后、资源复核前已过去；
 * APPROVE 必须在最终检查/激活前按当前 Clock 重查 targetFuture 并拒绝，否则活动被切到过去。
 */
class ChangeServiceTest {
    final UUID activityId = UUID.randomUUID(), ownerId = UUID.randomUUID(), operatorId = UUID.randomUUID();
    final UUID venue = UUID.randomUUID(), targetVenue = UUID.randomUUID(), org = UUID.randomUUID();
    final UUID changeId = UUID.randomUUID(), staffId = UUID.randomUUID();
    final Instant now = Instant.parse("2030-10-19T00:00:00Z");

    ChangeRepository changes; ActivityPort activities; AccountPort accounts;
    SchedulingPort scheduling; ResourceRepository resources; EventPort events;
    ChangeService service;
    static final jakarta.validation.ValidatorFactory VALIDATION = Validation.buildDefaultValidatorFactory();
    @AfterAll static void closeValidator() { VALIDATION.close(); }

    Actor operator() {
        return new Actor(Identity.TEACHER, List.of(Role.ADMIN), List.of(Permission.RESOURCE_CONFIRM),
                List.of(), List.of(targetVenue), operatorId, "资源复核员", org, true);
    }
    Actor owner() {
        return new Actor(Identity.TEACHER, List.of(Role.APPLICANT), List.of(), List.of(), List.of(),
                ownerId, "活动负责人", org, true);
    }
    /** 管理员但无 RESOURCE_CONFIRM 权限：鉴权必须先于版本冲突详情返回。 */
    Actor operatorWithoutConfirmPermission() {
        return new Actor(Identity.TEACHER, List.of(Role.ADMIN), List.of(), List.of(), List.of(targetVenue),
                operatorId, "无复核权限管理员", org, true);
    }
    Plan futurePlan() { return new Plan(now.plusSeconds(7200), now.plusSeconds(14400), 0, 0, venue, List.of()); }
    Plan futureTarget() { return new Plan(now.plusSeconds(10800), now.plusSeconds(18000), 0, 0, targetVenue, List.of()); }
    Plan pastTarget() { return new Plan(now.minusSeconds(3600), now.minusSeconds(1800), 0, 0, targetVenue, List.of()); }

    @BeforeEach void setup() {
        changes = mock(ChangeRepository.class);
        activities = mock(ActivityPort.class);
        accounts = mock(AccountPort.class);
        scheduling = mock(SchedulingPort.class);
        resources = mock(ResourceRepository.class);
        events = mock(EventPort.class);
        service = new ChangeService(changes, activities, accounts, scheduling, resources, events,
                VALIDATION.getValidator(), Clock.fixed(now, ZoneOffset.UTC));
    }

    @Test void resourceApproveRejectsTargetAlreadyInPastAtFinalReview() {
        var activity = new ActivityView(activityId, org, ownerId, ActivityStatus.SCHEDULED, 1, 1, 0,
                new ActivityInput("测试活动", "", 10, 1, List.of(ownerId), futurePlan()), 0, Risk.NORMAL, null, null, now);
        var impact = new Impact(futurePlan(), pastTarget(), List.of(), List.of(), List.of(), List.of(),
                List.of(), 0, List.of(), false, Risk.NORMAL, List.of());
        var row = new ChangeRepository.Row(changeId, activityId, 1, pastTarget(), impact, "改期",
                ChangeStatus.PENDING_RESOURCE, new DecisionRecord(Decision.APPROVE, "已批", operatorId, now),
                null, List.of(), null, ownerId, now, now);

        when(accounts.get(operatorId)).thenReturn(operator());
        when(accounts.lock(operatorId)).thenReturn(operator());
        when(accounts.lock(ownerId)).thenReturn(owner());
        when(changes.activityId(changeId)).thenReturn(activityId);
        when(activities.lock(activityId)).thenReturn(new ActivitySnapshot(activity, List.of()));
        when(changes.lock(changeId)).thenReturn(row);

        var ex = assertThrows(ApiException.class, () ->
                service.resourceDecision(operator(), changeId, new ChangeDecision(Decision.APPROVE, "同意")));
        assertEquals(409, ex.status());
        assertEquals("INVALID_STATE", ex.code());
        // 目标已过去的复核必须在调度锁/切换之前被拒绝，不得进入最终检查。
        verify(scheduling, never()).lockAndCheck(any(), any(), anyInt(), any());
    }

    @Test void ownerAsOnlyStaffSkipsSelfConfirmation() {
        var activity = new ActivityView(activityId, org, ownerId, ActivityStatus.SCHEDULED, 1, 1, 0,
                new ActivityInput("测试活动", "", 10, 1, List.of(ownerId), futurePlan()), 0, Risk.NORMAL, null, null, now);
        when(accounts.get(ownerId)).thenReturn(owner());
        when(activities.lock(activityId)).thenReturn(new ActivitySnapshot(activity, List.of()));
        when(accounts.lock(ownerId)).thenReturn(owner());
        when(changes.hasPending(activityId)).thenReturn(false);
        when(scheduling.risk(activityId)).thenReturn(Risk.NORMAL);
        var row = new ChangeRepository.Row(changeId, activityId, 1, futureTarget(),
                new Impact(futurePlan(), futureTarget(), List.of(), List.of(), List.of(), List.of(),
                        List.of(), 0, List.of(ownerId), true, Risk.NORMAL, List.of("startAt")),
                "改期", ChangeStatus.PENDING_APPROVER, null, null, List.of(), null, ownerId, now, now);
        when(changes.create(any(), eq(activityId), eq(1), any(), any(), eq("改期"), any(), eq(ownerId))).thenReturn(row);
        when(changes.get(changeId)).thenReturn(row);
        when(changes.confirmations(changeId)).thenReturn(List.of());

        var result = service.create(owner(), activityId, new ChangeCreate(1, futureTarget(), 0, "改期"));
        assertEquals(ChangeStatus.PENDING_APPROVER, result.status());

        ArgumentCaptor<Impact> impactCaptor = ArgumentCaptor.forClass(Impact.class);
        ArgumentCaptor<ChangeStatus> statusCaptor = ArgumentCaptor.forClass(ChangeStatus.class);
        verify(changes).create(any(), eq(activityId), eq(1), any(), impactCaptor.capture(), eq("改期"),
                statusCaptor.capture(), eq(ownerId));
        assertEquals(ChangeStatus.PENDING_APPROVER, statusCaptor.getValue());
        assertEquals(List.of(), impactCaptor.getValue().requiredStaffUserIds());
        verify(changes).createConfirmations(any(), eq(List.of()));
    }

    @Test void ownerPlusOtherStaffStillRequiresConfirmation() {
        var activity = new ActivityView(activityId, org, ownerId, ActivityStatus.SCHEDULED, 1, 1, 0,
                new ActivityInput("测试活动", "", 10, 1, List.of(ownerId, staffId), futurePlan()), 0, Risk.NORMAL, null, null, now);
        when(accounts.get(ownerId)).thenReturn(owner());
        when(activities.lock(activityId)).thenReturn(new ActivitySnapshot(activity, List.of()));
        when(accounts.lock(ownerId)).thenReturn(owner());
        when(changes.hasPending(activityId)).thenReturn(false);
        when(scheduling.risk(activityId)).thenReturn(Risk.NORMAL);
        var row = new ChangeRepository.Row(changeId, activityId, 1, futureTarget(),
                new Impact(futurePlan(), futureTarget(), List.of(), List.of(), List.of(), List.of(staffId),
                        List.of(), 0, List.of(ownerId, staffId), true, Risk.NORMAL, List.of("startAt")),
                "改期", ChangeStatus.AWAITING_CONFIRMATIONS, null, null, List.of(), null, ownerId, now, now);
        when(changes.create(any(), eq(activityId), eq(1), any(), any(), eq("改期"), any(), eq(ownerId))).thenReturn(row);
        when(changes.get(changeId)).thenReturn(row);
        when(changes.confirmations(changeId)).thenReturn(List.of());

        service.create(owner(), activityId, new ChangeCreate(1, futureTarget(), 0, "改期"));
        ArgumentCaptor<Impact> impactCaptor = ArgumentCaptor.forClass(Impact.class);
        ArgumentCaptor<ChangeStatus> statusCaptor = ArgumentCaptor.forClass(ChangeStatus.class);
        verify(changes).create(any(), eq(activityId), eq(1), any(), impactCaptor.capture(), eq("改期"),
                statusCaptor.capture(), eq(ownerId));
        assertEquals(ChangeStatus.AWAITING_CONFIRMATIONS, statusCaptor.getValue());
        assertEquals(List.of(staffId), impactCaptor.getValue().requiredStaffUserIds());
        verify(changes).createConfirmations(any(), eq(List.of(staffId)));
    }

    @Test void resourceDecisionChecksAuthorizationBeforeVersionConflict() {
        var activity = new ActivityView(activityId, org, ownerId, ActivityStatus.SCHEDULED, 2, 2, 4,
                new ActivityInput("测试活动", "", 10, 1, List.of(ownerId), futurePlan()), 0, Risk.NORMAL, null, null, now);
        var impact = new Impact(futurePlan(), futureTarget(), List.of(), List.of(), List.of(), List.of(),
                List.of(), 0, List.of(), false, Risk.NORMAL, List.of());
        var row = new ChangeRepository.Row(changeId, activityId, 1, futureTarget(), impact, "改期",
                ChangeStatus.PENDING_RESOURCE, new DecisionRecord(Decision.APPROVE, "已批", operatorId, now),
                null, List.of(), null, ownerId, now, now);

        when(accounts.get(operatorId)).thenReturn(operatorWithoutConfirmPermission());
        when(accounts.lock(operatorId)).thenReturn(operatorWithoutConfirmPermission());
        when(accounts.lock(ownerId)).thenReturn(owner());
        when(changes.activityId(changeId)).thenReturn(activityId);
        when(activities.lock(activityId)).thenReturn(new ActivitySnapshot(activity, List.of()));
        when(changes.lock(changeId)).thenReturn(row);

        var ex = assertThrows(ApiException.class, () ->
                service.resourceDecision(operatorWithoutConfirmPermission(), changeId, new ChangeDecision(Decision.APPROVE, "同意")));
        assertEquals(403, ex.status());
        // 鉴权失败必须在返回版本冲突详情之前，也不得进入最终调度检查。
        verify(scheduling, never()).lockAndCheck(any(), any(), anyInt(), any());
    }

    @Test void ownerCannotSelfConfirmLegacySnapshot() {
        var activity = new ActivityView(activityId, org, ownerId, ActivityStatus.SCHEDULED, 1, 1, 0,
                new ActivityInput("测试活动", "", 10, 1, List.of(ownerId), futurePlan()), 0, Risk.NORMAL, null, null, now);
        // 升级前旧快照：requiredStaffUserIds 仍含发起人本人，新快照会过滤掉（01 §3 无其他工作人员直接复审）。
        var legacyImpact = new Impact(futurePlan(), futureTarget(), List.of(), List.of(), List.of(), List.of(ownerId),
                List.of(), 0, List.of(ownerId), true, Risk.NORMAL, List.of("startAt"));
        var row = new ChangeRepository.Row(changeId, activityId, 1, futureTarget(), legacyImpact, "改期",
                ChangeStatus.AWAITING_CONFIRMATIONS, null, null, List.of(), null, ownerId, now, now);

        when(accounts.get(ownerId)).thenReturn(owner());
        when(accounts.lock(ownerId)).thenReturn(owner());
        when(changes.activityId(changeId)).thenReturn(activityId);
        when(activities.lock(activityId)).thenReturn(new ActivitySnapshot(activity, List.of()));
        when(changes.lock(changeId)).thenReturn(row);

        var ex = assertThrows(ApiException.class, () ->
                service.confirm(owner(), changeId, new ChangeDecision(Decision.APPROVE, "同意")));
        assertEquals(403, ex.status());
        // 不得自签/代签推进确认；存量申请处置为撤回重建，快照不可改。
        verify(changes, never()).decideConfirmation(any(), any(), any(), any(), any());
    }

    @Test void staleVersionReturnsCurrentVersionAndStatus() {
        var activity = new ActivityView(activityId, org, ownerId, ActivityStatus.SCHEDULED, 2, 2, 4,
                new ActivityInput("测试活动", "", 10, 1, List.of(ownerId), futurePlan()), 0, Risk.NORMAL, null, null, now);
        when(accounts.get(ownerId)).thenReturn(owner());
        when(activities.get(activityId)).thenReturn(new ActivitySnapshot(activity, List.of()));

        var ex = assertThrows(ApiException.class,
                () -> service.preview(owner(), activityId, new Proposal(1, futureTarget())));
        assertEquals(409, ex.status());
        assertEquals("STALE_VERSION", ex.code());
        @SuppressWarnings("unchecked")
        Map<String, Object> details = (Map<String, Object>) ex.details();
        assertEquals(2, details.get("versionNo"));
        assertEquals(4, details.get("lockVersion"));
        assertEquals("SCHEDULED", details.get("status"));
    }
}
