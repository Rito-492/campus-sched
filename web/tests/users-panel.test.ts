import { afterEach, describe, expect, it } from 'vitest'
import {
  flushPromises,
  mount,
  DOMWrapper,
  type VueWrapper,
} from '@vue/test-utils'
import ElementPlus from 'element-plus'
import UsersPanel from '../src/components/UsersPanel.vue'
import { ApiError } from '../src/api/client'
import { actor, resources } from './fixtures'
const user = actor({
  roles: ['ADMIN', 'APPROVER'],
  permissions: ['USER_MANAGE'],
  managedResourceIds: ['venue-1'],
  approvalScopes: [{ organizationId: 'org-1', resourceCategories: ['VENUE'] }],
})
let wrapper: VueWrapper | undefined
function panel(extra = {}) {
  wrapper = mount(UsersPanel, {
    attachTo: document.body,
    props: {
      users: [user],
      organizations: [{ id: 'org-1', name: '软件学院' }],
      resources,
      currentUserId: user.id,
      ready: true,
      ...extra,
    },
    global: { plugins: [ElementPlus] },
  })
  return wrapper
}
afterEach(() => {
  wrapper?.unmount()
  document.body.innerHTML = ''
})
const dialog = () => new DOMWrapper(document.body).get('[role="dialog"]')
describe('账号编辑表单', () => {
  it('空账号表单保留输入并显示校验，不发送创建事件', async () => {
    const w = panel()
    await w.get('[data-testid="user-create-open"]').trigger('click')
    await flushPromises()
    await dialog().get('[data-testid="user-display-name"]').setValue('新同学')
    await dialog().get('[data-testid="user-editor"]').trigger('submit')
    expect(document.body.textContent).toContain('请输入用户名')
    expect(document.body.textContent).toContain('初始密码至少8位')
    expect(w.emitted('submit')).toBeUndefined()
    expect(
      (
        dialog().get('[data-testid="user-display-name"]')
          .element as HTMLInputElement
      ).value,
    ).toBe('新同学')
  })
  it('必须确认完整替换；去掉管理员后提交清理字段，不携带Actor私有字段', async () => {
    const w = panel()
    await w.get('[data-testid="user-authorize-user-1"]').trigger('click')
    await flushPromises()
    await dialog().get('[data-testid="role-ADMIN"]').setValue(false)
    await dialog().get('[data-testid="user-editor"]').trigger('submit')
    expect(w.emitted('submit')).toBeUndefined()
    expect(document.body.textContent).toContain('请确认将完整替换')
    await dialog().get('[data-testid="authorization-confirm"]').setValue(true)
    await dialog().get('[data-testid="user-editor"]').trigger('submit')
    expect(w.emitted('submit')?.[0][0]).toEqual({
      kind: 'authorization',
      userId: 'user-1',
      input: {
        identity: 'TEACHER',
        roles: ['APPROVER'],
        permissions: [],
        managedResourceIds: [],
        approvalScopes: user.approvalScopes,
      },
    })
    expect(user.permissions).toEqual(['USER_MANAGE'])
  })
  it('服务端拒绝时保留编辑；只有确认完成后关闭并清除密码', async () => {
    const w = panel({ completed: 0 })
    await w.get('[data-testid="user-create-open"]').trigger('click')
    await flushPromises()
    await dialog()
      .get('[data-testid="user-password"]')
      .setValue('unit-test-password')
    await w.setProps({
      actionError: new ApiError(
        403,
        'FORBIDDEN',
        '授权范围已变化',
        'request-user',
      ),
    })
    expect(dialog().get('[data-testid="user-editor"]').isVisible()).toBe(true)
    expect(
      (
        dialog().get('[data-testid="user-password"]')
          .element as HTMLInputElement
      ).value,
    ).toBe('unit-test-password')
    expect(document.body.textContent).toContain('授权范围已变化')
    await w.setProps({ completed: 1, actionError: null })
    await flushPromises()
    await w.get('[data-testid="user-create-open"]').trigger('click')
    await flushPromises()
    expect(
      (
        dialog().get('[data-testid="user-password"]')
          .element as HTMLInputElement
      ).value,
    ).toBe('')
  })
  it('结果未知时锁定表单且只发原请求重试事件', async () => {
    const w = panel()
    await w.get('[data-testid="user-create-open"]').trigger('click')
    await flushPromises()
    await w.setProps({
      uncertain: true,
      actionError: new ApiError(0, 'NETWORK_ERROR', '结果尚未确认'),
    })
    expect(
      dialog().get('[data-testid="user-save"]').attributes('disabled'),
    ).toBeDefined()
    expect(
      dialog().get('[data-testid="user-username"]').attributes('disabled'),
    ).toBeDefined()
    await dialog().get('[data-testid="api-error"] button').trigger('click')
    expect(w.emitted('retry')).toHaveLength(1)
    expect(w.emitted('submit')).toBeUndefined()
  })
  it('开发预览即使触发表单提交也不执行账号写入', async () => {
    const w = panel({ preview: true })
    await w.get('[data-testid="user-authorize-user-1"]').trigger('click')
    await flushPromises()
    await dialog().get('[data-testid="authorization-confirm"]').setValue(true)
    await dialog().get('[data-testid="user-editor"]').trigger('submit')
    expect(w.emitted('submit')).toBeUndefined()
  })
})
