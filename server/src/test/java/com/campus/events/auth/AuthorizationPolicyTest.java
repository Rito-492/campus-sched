package com.campus.events.auth;

import com.campus.events.contract.*;
import com.campus.events.foundation.ApiException;
import java.util.*;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class AuthorizationPolicyTest {
    private final UUID reviewer = UUID.randomUUID(), owner = UUID.randomUUID(), org = UUID.randomUUID(), resource = UUID.randomUUID();
    private Actor actor(Identity identity, List<Role> roles, List<Permission> permissions, List<ApprovalScope> scopes, List<UUID> resources) {
        return new Actor(identity, roles, permissions, scopes, resources, reviewer, "测试人员", org, true);
    }
    @Test void teacherIdentityDoesNotGrantApproval() {
        var a = actor(Identity.TEACHER, List.of(Role.APPLICANT), List.of(), List.of(), List.of());
        assertThrows(ApiException.class, () -> AuthorizationPolicy.approval(a, owner, org, Set.of("VENUE")));
    }
    @Test void accountAdminHasNoResourceAuthority() {
        var a = actor(Identity.TEACHER, List.of(Role.ADMIN), List.of(Permission.USER_MANAGE), List.of(), List.of(resource));
        assertThrows(ApiException.class, () -> AuthorizationPolicy.confirmation(a, owner, Set.of(resource)));
    }
    @Test void permissionRequiresAdminRole() {
        var a = actor(Identity.STUDENT, List.of(Role.APPLICANT), List.of(Permission.RESOURCE_CONFIRM), List.of(), List.of(resource));
        assertThrows(ApiException.class, () -> AuthorizationPolicy.confirmation(a, owner, Set.of(resource)));
    }
    @Test void approvalRequiresWholeOrganizationAndCategoryCoverage() {
        var a = actor(Identity.STUDENT, List.of(Role.APPROVER), List.of(), List.of(new ApprovalScope(org, List.of("VENUE"))), List.of());
        assertThrows(ApiException.class, () -> AuthorizationPolicy.approval(a, owner, org, Set.of("VENUE", "PROJECTOR")));
        assertThrows(ApiException.class, () -> AuthorizationPolicy.approval(a, owner, UUID.randomUUID(), Set.of("VENUE")));
        assertDoesNotThrow(() -> AuthorizationPolicy.approval(a, owner, org, Set.of("VENUE")));
    }
    @Test void ownersCannotApproveOrConfirmEvenWithBothRoles() {
        var a = actor(Identity.TEACHER, List.of(Role.APPROVER, Role.ADMIN), List.of(Permission.RESOURCE_CONFIRM),
                List.of(new ApprovalScope(org, List.of("VENUE"))), List.of(resource));
        assertThrows(ApiException.class, () -> AuthorizationPolicy.approval(a, reviewer, org, Set.of("VENUE")));
        assertThrows(ApiException.class, () -> AuthorizationPolicy.confirmation(a, reviewer, Set.of(resource)));
    }
    @Test void confirmationRequiresEveryResource() {
        var a = actor(Identity.TEACHER, List.of(Role.ADMIN), List.of(Permission.RESOURCE_CONFIRM), List.of(), List.of(resource));
        assertThrows(ApiException.class, () -> AuthorizationPolicy.confirmation(a, owner, Set.of(resource, UUID.randomUUID())));
        assertDoesNotThrow(() -> AuthorizationPolicy.confirmation(a, owner, Set.of(resource)));
    }
    @Test void onlyStudentApplicantsCanRegister() {
        assertDoesNotThrow(() -> AuthorizationPolicy.registration(actor(Identity.STUDENT, List.of(Role.APPLICANT), List.of(), List.of(), List.of())));
        assertThrows(ApiException.class, () -> AuthorizationPolicy.registration(actor(Identity.TEACHER, List.of(Role.APPLICANT), List.of(), List.of(), List.of())));
    }
    @Test void disabledActorsCannotOperate() {
        var a = new Actor(Identity.STUDENT, List.of(Role.APPLICANT), List.of(), List.of(), List.of(), reviewer, "停用用户", org, false);
        assertEquals(401, assertThrows(ApiException.class, () -> AuthorizationPolicy.registration(a)).status());
    }
}
