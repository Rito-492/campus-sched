import { describe, expect, it } from 'vitest'
import {
  canReview,
  hasPermission,
  mayApprove,
  mayReserve,
  navigation,
  registrationBlock,
} from '../src/domain/access'
import { actor, activity, resources } from './fixtures'
describe('三角色、独立身份与范围', () => {
  it('教师身份不产生审批权限或待办导航', () => {
    expect(canReview(actor())).toBe(false)
    expect(
      navigation.find((n) => n.path === '/reviews')?.visible(actor()),
    ).toBe(false)
  })
  it('只有账号权限的管理员不能复核资源', () => {
    const a = actor({ roles: ['ADMIN'], permissions: ['USER_MANAGE'] })
    expect(hasPermission(a, 'USER_MANAGE')).toBe(true)
    expect(mayReserve(a, activity())).toBe(false)
    expect(canReview(a)).toBe(false)
  })
  it('权限列表脱离ADMIN角色不生效，停用账号也无权', () => {
    expect(
      hasPermission(actor({ permissions: ['USER_MANAGE'] }), 'USER_MANAGE'),
    ).toBe(false)
    expect(
      hasPermission(
        actor({
          roles: ['ADMIN'],
          enabled: false,
          permissions: ['USER_MANAGE'],
        }),
        'USER_MANAGE',
      ),
    ).toBe(false)
  })
  it('批准范围必须覆盖组织及全部资源类别，资源未加载时拒绝', () => {
    const a = actor({
      roles: ['APPROVER'],
      approvalScopes: [
        { organizationId: 'org-1', resourceCategories: ['VENUE'] },
      ],
    })
    expect(mayApprove(a, activity(), resources)).toBe(false)
    a.approvalScopes[0].resourceCategories.push('PROJECTOR')
    expect(mayApprove(a, activity(), resources)).toBe(true)
    expect(mayApprove(a, activity(), [])).toBe(false)
    expect(
      mayApprove(a, activity({ organizationId: 'org-2' }), resources),
    ).toBe(false)
  })
  it('完整资源范围才可复核，本人即使兼任两种角色也不可自审', () => {
    const a = actor({
      roles: ['APPLICANT', 'ADMIN', 'APPROVER'],
      permissions: ['RESOURCE_CONFIRM'],
      managedResourceIds: ['venue-1'],
      approvalScopes: [
        { organizationId: 'org-1', resourceCategories: ['VENUE', 'PROJECTOR'] },
      ],
    })
    expect(mayReserve(a, activity())).toBe(false)
    a.managedResourceIds.push('equipment-1')
    expect(mayReserve(a, activity())).toBe(true)
    expect(mayReserve(a, activity({ ownerId: a.id }))).toBe(false)
    expect(mayApprove(a, activity({ ownerId: a.id }), resources)).toBe(false)
  })
})
describe('报名入口', () => {
  const student = actor({ identity: 'STUDENT' }),
    now = Date.parse('2030-10-19T00:00:00Z')
  it('学生申请人可报名，教师不能', () => {
    expect(registrationBlock(student, activity(), now)).toBe('')
    expect(registrationBlock(actor(), activity(), now)).toContain('学生')
  })
  it('冻结同时阻止报名和退出', () => {
    expect(
      registrationBlock(
        student,
        activity({ activeChangeId: 'change', myRegistrationStatus: 'ACTIVE' }),
        now,
      ),
    ).toContain('暂停')
  })
  it('满额不能新报名，但已有报名可以退出', () => {
    expect(
      registrationBlock(student, activity({ registrationCount: 20 }), now),
    ).toContain('已满')
    expect(
      registrationBlock(
        student,
        activity({ registrationCount: 20, myRegistrationStatus: 'ACTIVE' }),
        now,
      ),
    ).toBe('')
  })
  it('未开放、已开始或停用时拒绝', () => {
    expect(
      registrationBlock(student, activity({ status: 'SCHEDULED' }), now),
    ).toContain('尚未')
    expect(
      registrationBlock(
        student,
        activity(),
        Date.parse('2030-10-20T03:00:00Z'),
      ),
    ).toContain('已开始')
    expect(
      registrationBlock({ ...student, enabled: false }, activity(), now),
    ).not.toBe('')
  })
})
