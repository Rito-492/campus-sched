package com.campus.events.scheduling;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import com.campus.events.resource.ResourceRepository;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ScheduleQueryServiceTest {
    final UUID owner = UUID.randomUUID(), reviewer = UUID.randomUUID(), org = UUID.randomUUID();
    final UUID activity = UUID.randomUUID(), venue = UUID.randomUUID(), equipment = UUID.randomUUID();
    final Instant start = Instant.parse("2030-10-20T02:00:00Z");
    final Plan plan = new Plan(start, start.plusSeconds(3600), 0, 0, venue, List.of(new EquipmentNeed(equipment, 1)));
    AccountPort accounts;
    SchedulingPort scheduling;
    ScheduleQueryService queries;

    Actor actor(UUID id, Role role, Identity identity, List<ApprovalScope> scopes) {
        return new Actor(identity, List.of(role), List.of(), scopes, List.of(), id, "测试账号", org, true);
    }

    @BeforeEach void setup() {
        accounts = mock(AccountPort.class);
        scheduling = mock(SchedulingPort.class);
        ActivityPort activities = mock(ActivityPort.class);
        ResourceRepository resources = mock(ResourceRepository.class);
        when(activities.get(activity)).thenReturn(new ActivitySnapshot(new ActivityView(activity, org, owner,
                ActivityStatus.SCHEDULED, 2, 2, 4, new ActivityInput("测试", "", 60, 20, List.of(), plan),
                0, Risk.NORMAL, null, null, start), List.of()));
        when(resources.get(venue)).thenReturn(new ResourceView(true, true, ResourceKind.VENUE, "场地", "VENUE", 80, 1,
                "08:00", "22:00", venue, start));
        when(resources.get(equipment)).thenReturn(new ResourceView(true, true, ResourceKind.EQUIPMENT, "设备", "PROJECTOR", null, 2,
                "08:00", "22:00", equipment, start));
        when(accounts.get(owner)).thenReturn(actor(owner, Role.APPLICANT, Identity.STUDENT, List.of()));
        var beans = new StaticListableBeanFactory();
        beans.addBean("activities", activities);
        queries = new ScheduleQueryService(beans.getBeanProvider(ActivityPort.class), accounts, resources, scheduling);
    }

    @Test void ownerPreviewUsesLatestIdentityAndDoesNotReserve() {
        queries.preview(actor(owner, Role.APPLICANT, Identity.TEACHER, List.of()), activity, new Proposal(2, plan), false);
        verify(scheduling).check(plan, 60, activity, Identity.STUDENT);
        verifyNoMoreInteractions(scheduling);
    }

    @Test void teacherIdentityDoesNotGrantApprovalAndIncompleteScopeIsRejected() {
        Actor outsider = actor(reviewer, Role.APPLICANT, Identity.TEACHER, List.of());
        when(accounts.get(reviewer)).thenReturn(outsider);
        assertEquals(403, assertThrows(ApiException.class,
                () -> queries.preview(outsider, activity, new Proposal(2, plan), false)).status());
        Actor partial = actor(reviewer, Role.APPROVER, Identity.TEACHER, List.of(new ApprovalScope(org, List.of("VENUE"))));
        when(accounts.get(reviewer)).thenReturn(partial);
        assertEquals(403, assertThrows(ApiException.class,
                () -> queries.preview(partial, activity, new Proposal(2, plan), true)).status());
        verifyNoInteractions(scheduling);
    }

    @Test void authorizedReviewerCanPreviewAlternativesUsingOwnersIdentity() {
        Actor approved = actor(reviewer, Role.APPROVER, Identity.TEACHER,
                List.of(new ApprovalScope(org, List.of("VENUE", "PROJECTOR"))));
        when(accounts.get(reviewer)).thenReturn(approved);
        queries.preview(approved, activity, new Proposal(2, plan), true);
        verify(scheduling).alternatives(plan, 60, activity, Identity.STUDENT);
        verifyNoMoreInteractions(scheduling);
    }

    @Test void staleVersionStopsBeforeResourceEvaluation() {
        assertEquals("STALE_VERSION", assertThrows(ApiException.class, () -> queries.preview(
                actor(owner, Role.APPLICANT, Identity.STUDENT, List.of()), activity, new Proposal(1, plan), false)).code());
        verifyNoInteractions(scheduling);
    }
}
