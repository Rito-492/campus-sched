import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import AuthorizationEditor from '../src/components/AuthorizationEditor.vue'
import {
  copyAuthorization,
  emptyAuthorization,
  validateAuthorization,
  validateUserCreate,
  withRoles,
} from '../src/domain/authorization'
import { actor, resources } from './fixtures'
describe('完整授权替换', () => {
  it('移除ADMIN清理权限和资源，保留仍担任的批准人范围，不修改原数据', () => {
    const original = actor({
      roles: ['ADMIN', 'APPROVER'],
      permissions: ['RESOURCE_CONFIRM'],
      managedResourceIds: ['venue-1'],
      approvalScopes: [
        { organizationId: 'org-1', resourceCategories: ['VENUE'] },
      ],
    })
    const next = withRoles(original, ['APPROVER'])
    expect(next.permissions).toEqual([])
    expect(next.managedResourceIds).toEqual([])
    expect(next.approvalScopes).toEqual(original.approvalScopes)
    expect(original.permissions).toEqual(['RESOURCE_CONFIRM'])
    expect(withRoles(next, ['ADMIN', 'APPROVER']).permissions).toEqual([])
  })
  it('移除APPROVER清空审批范围，不影响管理员细分权限', () => {
    const next = withRoles(
      actor({
        roles: ['ADMIN', 'APPROVER'],
        permissions: ['USER_MANAGE'],
        approvalScopes: [
          { organizationId: 'org-1', resourceCategories: ['VENUE'] },
        ],
      }),
      ['ADMIN'],
    )
    expect(next.approvalScopes).toEqual([])
    expect(next.permissions).toEqual(['USER_MANAGE'])
  })
  it('授权副本仅含五个共享字段，深复制范围，不携带actorId或启停状态', () => {
    const original = actor({
      approvalScopes: [
        { organizationId: 'org-1', resourceCategories: ['VENUE'] },
      ],
    })
    const copy = copyAuthorization(original)
    copy.approvalScopes[0].resourceCategories.push('PROJECTOR')
    expect(Object.keys(copy).sort()).toEqual(
      [
        'approvalScopes',
        'identity',
        'managedResourceIds',
        'permissions',
        'roles',
      ].sort(),
    )
    expect(original.approvalScopes[0].resourceCategories).toEqual(['VENUE'])
  })
  it('拒绝重复组织、空类别和通配授权', () => {
    const value = {
      ...emptyAuthorization(),
      roles: ['APPROVER' as const],
      approvalScopes: [
        { organizationId: 'org-1', resourceCategories: ['*'] },
        { organizationId: 'org-1', resourceCategories: [] },
      ],
    }
    expect(validateAuthorization(value)).toHaveLength(2)
  })
  it('账号校验遵守8位及72 UTF-8字节，密码不被trim或截断', () => {
    const input = {
      ...emptyAuthorization(),
      username: 'student',
      displayName: '同学',
      password: '字'.repeat(24),
      organizationId: 'org-1',
    }
    expect(validateUserCreate(input)).toEqual([])
    expect(
      validateUserCreate({ ...input, password: '字'.repeat(25) }),
    ).toContain('初始密码不能超过72个UTF-8字节')
    expect(validateUserCreate({ ...input, password: '1234567' })).toContain(
      '初始密码至少8位',
    )
  })
  it('教师身份切换不增加APPROVER角色', async () => {
    const wrapper = mount(AuthorizationEditor, {
      props: {
        modelValue: emptyAuthorization(),
        organizations: [],
        resources: [],
      },
      global: { plugins: [ElementPlus] },
    })
    await wrapper
      .get('[data-testid="authorization-identity"]')
      .setValue('TEACHER')
    expect(wrapper.emitted('update:modelValue')?.[0][0]).toMatchObject({
      identity: 'TEACHER',
      roles: ['APPLICANT'],
      permissions: [],
    })
    expect(wrapper.find('[data-testid="scope-add"]').exists()).toBe(false)
  })
  it('截断列表未返回的既有资源范围仍保留，未被静默缩减', async () => {
    const original = actor({
      roles: ['ADMIN'],
      permissions: ['RESOURCE_MANAGE'],
      managedResourceIds: ['venue-1', 'unlisted-resource'],
    })
    const wrapper = mount(AuthorizationEditor, {
      props: { modelValue: original, organizations: [], resources },
      global: { plugins: [ElementPlus] },
    })
    await wrapper
      .get('[data-testid="authorization-identity"]')
      .setValue('STUDENT')
    expect(wrapper.emitted('update:modelValue')?.[0][0]).toMatchObject({
      managedResourceIds: ['venue-1', 'unlisted-resource'],
    })
  })
})
