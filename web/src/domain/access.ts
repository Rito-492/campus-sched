import type {
  Actor,
  ActivityView,
  Permission,
  ResourceView,
} from '../types/api'
export const hasPermission = (actor: Actor | null, permission: Permission) =>
  !!actor?.enabled &&
  actor.roles.includes('ADMIN') &&
  actor.permissions.includes(permission)
export const isApplicant = (actor: Actor | null) =>
  !!actor?.enabled && actor.roles.includes('APPLICANT')
export const canReview = (actor: Actor | null) =>
  !!actor?.enabled &&
  ((actor.roles.includes('APPROVER') && actor.approvalScopes.length > 0) ||
    (hasPermission(actor, 'RESOURCE_CONFIRM') &&
      actor.managedResourceIds.length > 0))
export function mayApprove(
  actor: Actor | null,
  activity: ActivityView,
  resources: ResourceView[],
) {
  if (
    !actor?.enabled ||
    actor.id === activity.ownerId ||
    !actor.roles.includes('APPROVER')
  )
    return false
  const ids = [
    activity.input.plan.venueId,
    ...activity.input.plan.equipment.map((item) => item.resourceId),
  ]
  const categories = ids.map(
    (id) => resources.find((resource) => resource.id === id)?.category,
  )
  return categories.every(
    (category) =>
      category &&
      actor.approvalScopes.some(
        (scope) =>
          scope.organizationId === activity.organizationId &&
          scope.resourceCategories.includes(category),
      ),
  )
}
export function mayReserve(actor: Actor | null, activity: ActivityView) {
  return (
    !!actor &&
    actor.id !== activity.ownerId &&
    hasPermission(actor, 'RESOURCE_CONFIRM') &&
    [
      activity.input.plan.venueId,
      ...activity.input.plan.equipment.map((item) => item.resourceId),
    ].every((id) => actor.managedResourceIds.includes(id))
  )
}
export function registrationBlock(
  actor: Actor | null,
  activity: ActivityView,
  now = Date.now(),
) {
  if (!isApplicant(actor) || actor?.identity !== 'STUDENT')
    return '仅学生身份的申请人可报名或退出'
  if (activity.activeChangeId) return '改期处理中，报名和退出暂停'
  if (activity.status !== 'OPEN') return '活动尚未开放报名'
  if (Date.parse(activity.input.plan.startAt) <= now)
    return '活动已开始，不能报名或退出'
  if (
    activity.myRegistrationStatus !== 'ACTIVE' &&
    activity.registrationCount >= activity.input.registrationCapacity
  )
    return '报名已满'
  return ''
}
export const navigation = [
  {
    path: '/activities',
    label: '活动工作台',
    section: '协同工作',
    visible: (_: Actor | null) => true,
  },
  {
    path: '/resources/calendar',
    label: '资源日历',
    section: '协同工作',
    visible: (_: Actor | null) => true,
  },
  {
    path: '/reviews',
    label: '审批与待办',
    section: '协同工作',
    visible: canReview,
  },
  {
    path: '/notifications',
    label: '通知中心',
    section: '协同工作',
    visible: (_: Actor | null) => true,
  },
  {
    path: '/resources/manage',
    label: '资源管理',
    section: '管理',
    visible: (actor: Actor | null) => hasPermission(actor, 'RESOURCE_MANAGE'),
  },
  {
    path: '/admin/users',
    label: '账号与授权',
    section: '管理',
    visible: (actor: Actor | null) => hasPermission(actor, 'USER_MANAGE'),
  },
]
