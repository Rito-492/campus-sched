import { describe, expect, it } from 'vitest'
import { changeActions, mayChange, recordAccess } from '../src/domain/change'
import type { ChangeView } from '../src/types/api'
import { actor, activity, resources } from './fixtures'
export function change(overrides: Partial<ChangeView> = {}): ChangeView {
  const a = activity()
  return {
    id: 'change-1',
    activityId: a.id,
    baseVersionNo: a.versionNo,
    targetPlan: a.input.plan,
    reason: '资源故障',
    status: 'AWAITING_CONFIRMATIONS',
    impact: {
      oldPlan: a.input.plan,
      newPlan: a.input.plan,
      releasedResourceIds: [],
      acquiredResourceIds: [],
      retainedResourceIds: [],
      requiredStaffUserIds: ['staff-1'],
      registeredUserIds: [],
      registeredCount: 12,
      notificationUserIds: [],
      approvalReset: true,
      originalResourceRisk: 'NORMAL',
      changedFields: [],
    },
    confirmations: [
      { userId: 'staff-1', decision: null, comment: '', decidedAt: null },
    ],
    approverDecision: null,
    resourceDecision: null,
    lastConflicts: [],
    appliedVersionNo: null,
    createdBy: a.ownerId,
    createdAt: a.createdAt,
    updatedAt: a.createdAt,
    ...overrides,
  }
}
describe('改期权限与状态', () => {
  const a = activity({ activeChangeId: 'change-1' })
  it('工作人员只能确认本人尚未处理的步骤', () => {
    expect(
      changeActions(actor({ id: 'staff-1' }), a, change(), resources).confirm,
    ).toBe(true)
    expect(changeActions(actor(), a, change(), resources).confirm).toBe(false)
    expect(
      changeActions(
        actor({ id: 'staff-1' }),
        a,
        change({
          confirmations: [
            {
              userId: 'staff-1',
              decision: 'APPROVE',
              comment: '',
              decidedAt: a.createdAt,
            },
          ],
        }),
        resources,
      ).confirm,
    ).toBe(false)
  })
  it('批准范围检查目标的全部资源类别且本人回避', () => {
    const approver = actor({
        roles: ['APPROVER'],
        approvalScopes: [
          {
            organizationId: 'org-1',
            resourceCategories: ['VENUE', 'PROJECTOR'],
          },
        ],
      }),
      c = change({ status: 'PENDING_APPROVER' })
    expect(changeActions(approver, a, c, resources).approve).toBe(true)
    expect(
      changeActions({ ...approver, id: a.ownerId }, a, c, resources).approve,
    ).toBe(false)
    expect(
      changeActions(
        approver,
        a,
        { ...c, targetPlan: { ...c.targetPlan, venueId: 'unknown-target' } },
        resources,
      ).approve,
    ).toBe(false)
  })
  it('资源复核不从其他管理权限推导，目标范围要完整', () => {
    const admin = actor({
        roles: ['ADMIN'],
        permissions: ['RESOURCE_CONFIRM'],
        managedResourceIds: ['venue-1', 'equipment-1'],
      }),
      c = change({ status: 'PENDING_RESOURCE' })
    expect(changeActions(admin, a, c, resources).reserve).toBe(true)
    expect(
      changeActions(
        { ...admin, permissions: ['RESOURCE_MANAGE'] },
        a,
        c,
        resources,
      ).reserve,
    ).toBe(false)
    expect(
      changeActions(
        { ...admin, managedResourceIds: ['venue-1'] },
        a,
        c,
        resources,
      ).reserve,
    ).toBe(false)
  })
  it('结束、过期或不再是当前改期时不允许操作', () => {
    const staff = actor({ id: 'staff-1' })
    for (const c of [
      change({ status: 'REJECTED' }),
      change({ id: 'old-change' }),
    ])
      expect(
        Object.values(changeActions(staff, a, c, resources)).some(Boolean),
      ).toBe(false)
    expect(
      changeActions(
        staff,
        a,
        change(),
        resources,
        Date.parse(a.input.plan.startAt),
      ).confirm,
    ).toBe(false)
  })
  it('仅本人申请人可以发起或撤回，待处理改期阻止新建', () => {
    const owner = actor({ id: a.ownerId })
    expect(changeActions(owner, a, change(), resources).withdraw).toBe(true)
    expect(mayChange(owner, a)).toBe(false)
    expect(mayChange(owner, { ...a, activeChangeId: null })).toBe(true)
    expect(mayChange(actor(), { ...a, activeChangeId: null })).toBe(false)
  })
  it('报名学生不获得名单与审计权限，审计单独检查资源范围', () => {
    expect(recordAccess(actor({ identity: 'STUDENT' }), a, resources)).toEqual({
      registrations: false,
      changes: false,
      audit: false,
    })
    expect(
      recordAccess(
        actor({
          roles: ['ADMIN'],
          permissions: ['AUDIT_READ'],
          managedResourceIds: ['venue-1'],
        }),
        a,
        resources,
      ).audit,
    ).toBe(false)
  })
})
