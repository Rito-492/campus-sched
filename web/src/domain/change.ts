import type {
  Actor,
  ActivityView,
  ChangeView,
  ResourceView,
  Plan,
} from '../types/api'
import { hasPermission, isApplicant, mayApprove, mayReserve } from './access'
export const pendingChange = (status: ChangeView['status']) =>
  ['AWAITING_CONFIRMATIONS', 'PENDING_APPROVER', 'PENDING_RESOURCE'].includes(
    status,
  )
export const changeLabels: Record<ChangeView['status'], string> = {
  AWAITING_CONFIRMATIONS: '等待工作人员确认',
  PENDING_APPROVER: '等待批准人复审',
  PENDING_RESOURCE: '等待资源确认',
  APPLIED: '改期已生效',
  REJECTED: '已拒绝',
  CONFLICTED: '资源冲突',
  WITHDRAWN: '已撤回',
  CANCELLED_BY_ACTIVITY: '活动取消，申请关闭',
}
export function changeActions(
  actor: Actor | null,
  activity: ActivityView,
  change: ChangeView,
  resources: ResourceView[],
  now = Date.now(),
) {
  const active =
    actor?.enabled &&
    pendingChange(change.status) &&
    activity.activeChangeId === change.id &&
    ['SCHEDULED', 'OPEN'].includes(activity.status) &&
    Date.parse(activity.input.plan.startAt) > now
  const target = {
    ...activity,
    input: { ...activity.input, plan: change.targetPlan },
  }
  return {
    confirm:
      !!active &&
      change.status === 'AWAITING_CONFIRMATIONS' &&
      change.confirmations.some(
        (c) => c.userId === actor?.id && c.decision === null,
      ),
    approve:
      !!active &&
      change.status === 'PENDING_APPROVER' &&
      mayApprove(actor, target, resources),
    reserve:
      !!active &&
      change.status === 'PENDING_RESOURCE' &&
      mayReserve(actor, target),
    withdraw: !!active && isApplicant(actor) && actor?.id === activity.ownerId,
  }
}
export function mayChange(
  actor: Actor | null,
  activity: ActivityView,
  now = Date.now(),
) {
  return (
    isApplicant(actor) &&
    actor?.id === activity.ownerId &&
    ['SCHEDULED', 'OPEN'].includes(activity.status) &&
    !activity.activeChangeId &&
    Date.parse(activity.input.plan.startAt) > now
  )
}
export const planKey = (plan: Plan) =>
  JSON.stringify({
    startAt: Date.parse(plan.startAt),
    endAt: Date.parse(plan.endAt),
    setupMinutes: plan.setupMinutes,
    teardownMinutes: plan.teardownMinutes,
    venueId: plan.venueId,
    equipment: [...plan.equipment].sort((a, b) =>
      a.resourceId.localeCompare(b.resourceId),
    ),
  })
export function recordAccess(
  actor: Actor | null,
  activity: ActivityView,
  resources: ResourceView[],
) {
  const owner = actor?.enabled && actor.id === activity.ownerId
  const duty =
    mayApprove(actor, activity, resources) || mayReserve(actor, activity)
  const audit =
    hasPermission(actor, 'AUDIT_READ') &&
    [
      activity.input.plan.venueId,
      ...activity.input.plan.equipment.map((e) => e.resourceId),
    ].every((id) => actor!.managedResourceIds.includes(id))
  return {
    registrations: !!owner || duty,
    changes:
      !!owner ||
      duty ||
      (!!actor?.enabled && activity.input.staffUserIds.includes(actor.id)),
    audit: !!owner || duty || audit,
  }
}
