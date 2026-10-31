import type {
  Actor,
  AuthorizationInput,
  Permission,
  Role,
  UserCreate,
} from '../types/api'
import { hasPermission } from './access'

export const roleLabels: Record<Role, string> = {
  APPLICANT: '申请人',
  APPROVER: '批准人',
  ADMIN: '管理员',
}
export const permissionLabels: Record<Permission, string> = {
  USER_MANAGE: '账号管理',
  RESOURCE_MANAGE: '资源管理',
  RESOURCE_CONFIRM: '资源复核',
  AUDIT_READ: '审计查看',
}
export type OrganizationOption = { id: string; name: string }
export type UserOperation =
  | { kind: 'create'; input: UserCreate }
  | { kind: 'authorization'; userId: string; input: AuthorizationInput }
  | { kind: 'enabled'; userId: string; enabled: boolean }

export const emptyAuthorization = (): AuthorizationInput => ({
  identity: 'STUDENT',
  roles: ['APPLICANT'],
  permissions: [],
  approvalScopes: [],
  managedResourceIds: [],
})
export function copyAuthorization(
  value: AuthorizationInput,
): AuthorizationInput {
  return {
    identity: value.identity,
    roles: [...value.roles],
    permissions: [...value.permissions],
    approvalScopes: value.approvalScopes.map((scope) => ({
      organizationId: scope.organizationId,
      resourceCategories: [...scope.resourceCategories],
    })),
    managedResourceIds: [...value.managedResourceIds],
  }
}
export function withRoles(
  value: AuthorizationInput,
  roles: Role[],
): AuthorizationInput {
  const next = copyAuthorization(value)
  next.roles = [...new Set(roles)]
  if (!roles.includes('ADMIN')) {
    next.permissions = []
    next.managedResourceIds = []
  }
  if (!roles.includes('APPROVER')) next.approvalScopes = []
  return next
}
export function validateAuthorization(value: AuthorizationInput): string[] {
  const errors: string[] = []
  if (!['TEACHER', 'STUDENT'].includes(value.identity))
    errors.push('请选择教师或学生身份')
  if (
    !value.roles.includes('ADMIN') &&
    (value.permissions.length || value.managedResourceIds.length)
  )
    errors.push('非管理员不能保留管理员权限或资源范围')
  if (!value.roles.includes('APPROVER') && value.approvalScopes.length)
    errors.push('非批准人不能保留审批范围')
  if (
    new Set(value.approvalScopes.map((s) => s.organizationId)).size !==
    value.approvalScopes.length
  )
    errors.push('同一组织的审批范围只能填写一次')
  if (
    value.approvalScopes.some(
      (s) =>
        !s.organizationId ||
        !s.resourceCategories.length ||
        s.resourceCategories.some((c) => !c.trim() || c === '*'),
    )
  )
    errors.push('每项审批范围须明确选择组织和资源类别，不支持全范围通配')
  return errors
}
export function validateUserCreate(value: UserCreate): string[] {
  const errors = validateAuthorization(value)
  if (!value.username.trim()) errors.push('请输入用户名')
  if (!value.displayName.trim()) errors.push('请输入显示名')
  if (!value.organizationId) errors.push('请选择所属组织')
  if (value.password.length < 8) errors.push('初始密码至少8位')
  if (new TextEncoder().encode(value.password).length > 72)
    errors.push('初始密码不能超过72个UTF-8字节')
  return errors
}
export function authorizationSummary(actor: AuthorizationInput) {
  return `${actor.identity === 'TEACHER' ? '教师' : '学生'}；角色：${actor.roles.map((r) => roleLabels[r]).join('、') || '无'}；管理权限：${actor.permissions.map((p) => permissionLabels[p]).join('、') || '无'}；审批组织 ${actor.approvalScopes.length} 项；管理资源 ${actor.managedResourceIds.length} 项`
}
export function hasAccountAccess(actor: Actor | null) {
  return hasPermission(actor, 'USER_MANAGE')
}
