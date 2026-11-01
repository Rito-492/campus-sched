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

/**
 * 改期资源复核「目标仍须在未来」回归（评审 P1）。
 *
 * <p>场景：原活动仍在未来（future(activity) 通过），但改期目标在创建后、资源复核前已过去；
 * APPROVE 必须在最终检查/激活前按当前 Clock 重查 targetFuture 并拒绝，否则活动被切到过去。
 */
class ChangeServiceTest {
    final UUID activityId = UUID.randomUUID(), ownerId = UUID.randomUUID(), operatorId = UUID.randomUUID();
    final UUID venue = UUID.randomUUID(), targetVenue = UUID.randomUUID(), org = UUID.randomUUID();
    final UUID changeId = UUID.randomUUID();
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
    Plan futurePlan() { return new Plan(now.plusSeconds(7200), now.plusSeconds(14400), 0, 0, venue, List.of()); }
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
}
