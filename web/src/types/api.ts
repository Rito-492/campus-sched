// 由 agents/common/02 的 TypeScript 契约生成；npm run types:sync 更新。
export type UUID = string
export type Instant = string
export type Role = 'APPLICANT'|'APPROVER'|'ADMIN'
export type Identity = 'TEACHER'|'STUDENT'
export type Permission = 'USER_MANAGE'|'RESOURCE_MANAGE'|'RESOURCE_CONFIRM'|'AUDIT_READ'
export interface ApprovalScope { organizationId: UUID; resourceCategories: string[] }
export interface AuthorizationInput {
  identity: Identity; roles: Role[]; permissions: Permission[];
  approvalScopes: ApprovalScope[]; managedResourceIds: UUID[]
}
export interface UserCreate extends AuthorizationInput {
  username: string; displayName: string; password: string; organizationId: UUID
}
export interface ResourceEligibility { allowTeacher: boolean; allowStudent: boolean }
export type ActivityStatus = 'DRAFT'|'PENDING_APPROVER'|'APPROVED_UNRESERVED'|'SCHEDULED'|'OPEN'|'CANCELLED'
export type ChangeStatus = 'AWAITING_CONFIRMATIONS'|'PENDING_APPROVER'|'PENDING_RESOURCE'|'APPLIED'|'REJECTED'|'CONFLICTED'|'WITHDRAWN'|'CANCELLED_BY_ACTIVITY'
export type Decision = 'APPROVE'|'REJECT'
export type Risk = 'NORMAL'|'AT_RISK'
export type ResourceKind = 'VENUE'|'EQUIPMENT'
export interface StaffView { id: UUID; displayName: string; organizationId: UUID; enabled: boolean }
export interface Actor extends AuthorizationInput { id: UUID; displayName: string; organizationId: UUID; enabled: boolean }
export interface EquipmentNeed { resourceId: UUID; quantity: number }
export interface Plan {
  startAt: Instant; endAt: Instant; setupMinutes: number; teardownMinutes: number;
  venueId: UUID; equipment: EquipmentNeed[]
}
export interface ActivityInput {
  title: string; description: string; expectedAttendees: number;
  registrationCapacity: number; staffUserIds: UUID[]; plan: Plan
}
export interface ActivityView {
  id: UUID; organizationId: UUID; ownerId: UUID; status: ActivityStatus;
  versionNo: number; effectiveVersionNo: number|null; lockVersion: number;
  input: ActivityInput; registrationCount: number; resourceRisk: Risk;
  activeChangeId: UUID|null; myRegistrationStatus: 'ACTIVE'|'WITHDRAWN'|'CANCELLED'|null;
  createdAt: Instant
}
export interface VersionCommand { expectedLockVersion: number }
export interface ReasonCommand extends VersionCommand { reason: string }
export interface ActivityEdit extends VersionCommand { input: ActivityInput }
export interface ApprovalCommand extends VersionCommand { decision: Decision; comment: string }
export interface ResourceCreate extends ResourceEligibility {
  kind: ResourceKind; name: string; category: string;
  capacity: number|null; quantity: number; openFrom: string; openTo: string
}
export interface ResourceView extends ResourceCreate { id: UUID; createdAt: Instant }
export interface FaultInput { startAt: Instant; endAt: Instant; unavailableQuantity: number; reason: string }
export interface AffectedActivity { activityId: UUID; title: string; versionNo: number; resourceRisk: Risk }
export interface FaultView extends FaultInput { id: UUID; resourceId: UUID; affectedActivities: AffectedActivity[] }
export interface CalendarEntry {
  resourceId: UUID; startsAt: Instant; endsAt: Instant; quantity: number;
  activityId: UUID|null; label: string; risk: Risk; segment: 'SETUP'|'EVENT'|'TEARDOWN'|'FAULT'
}
export type ConflictCode = 'VENUE_CAPACITY'|'OUTSIDE_HOURS'|'VENUE_OCCUPIED'|'EQUIPMENT_SHORTAGE'|'RESOURCE_FAULT'
export interface Conflict {
  code: ConflictCode; resourceId: UUID; resourceName: string; startsAt: Instant;
  endsAt: Instant; required: number|null; available: number|null; message: string
}
export interface CheckResult { feasible: boolean; conflicts: Conflict[] }
export interface Proposal {
  baseVersionNo: number; targetPlan: Plan
}
export interface Alternative {
  plan: Plan; timeShiftMinutes: number; venueChanged: boolean;
  replacedEquipmentLines: number; explanation: string
}
export interface AlternativesView {
  items: Alternative[]; evaluatedCount: number; truncated: boolean;
  offsetsMinutes: number[]; maxEvaluations: number
}
export interface Impact {
  oldPlan: Plan; newPlan: Plan; releasedResourceIds: UUID[]; acquiredResourceIds: UUID[];
  retainedResourceIds: UUID[]; requiredStaffUserIds: UUID[];
  registeredUserIds: UUID[]; registeredCount: number; notificationUserIds: UUID[];
  approvalReset: true; originalResourceRisk: Risk; changedFields: string[]
}
export interface ChangePreview { check: CheckResult; alternatives: AlternativesView; impact: Impact }
export interface ChangeCreate extends Proposal { expectedLockVersion: number; reason: string }
export interface Confirmation { userId: UUID; decision: Decision|null; comment: string; decidedAt: Instant|null }
export interface ChangeDecision { decision: Decision; comment: string }
export interface DecisionRecord extends ChangeDecision { actorId: UUID; decidedAt: Instant }
export interface ChangeView {
  id: UUID; activityId: UUID; baseVersionNo: number; targetPlan: Plan;
  reason: string; status: ChangeStatus; impact: Impact; confirmations: Confirmation[];
  approverDecision: DecisionRecord|null; resourceDecision: DecisionRecord|null;
  lastConflicts: Conflict[]; appliedVersionNo: number|null; createdBy: UUID;
  createdAt: Instant; updatedAt: Instant
}
export interface RegistrationCommand { action: 'JOIN'|'WITHDRAW'; expectedVersionNo: number }
export interface RegistrationView { activityId: UUID; userId: UUID; status: 'ACTIVE'|'WITHDRAWN'|'CANCELLED'; versionNo: number }
export interface NotificationView {
  id: UUID; eventId: UUID; activityId: UUID|null; versionNo: number|null;
  title: string; body: string; readAt: Instant|null; createdAt: Instant
}
export interface AuditView {
  id: UUID; activityId: UUID; actorId: UUID; eventType: string;
  versionNo: number; summary: string; createdAt: Instant
}
