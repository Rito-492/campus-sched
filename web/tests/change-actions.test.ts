import { afterEach, describe, expect, it } from 'vitest'
import { mount, DOMWrapper } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ChangeActions from '../src/components/ChangeActions.vue'
import type { ChangeView } from '../src/types/api'
import { actor, activity, resources } from './fixtures'
const wrappers: ReturnType<typeof mount>[] = []
function setup(preview = false) {
  const a = activity({ activeChangeId: 'c' })
  const wrapper = mount(ChangeActions, {
    attachTo: document.body,
    props: {
      actor: actor({ id: 'staff' }),
      activity: a,
      change: {
        id: 'c',
        status: 'AWAITING_CONFIRMATIONS',
        targetPlan: a.input.plan,
        confirmations: [{ userId: 'staff', decision: null }],
      } as ChangeView,
      resources,
      preview,
    },
    global: { plugins: [ElementPlus] },
  })
  wrappers.push(wrapper)
  return wrapper
}
afterEach(() => wrappers.splice(0).forEach((w) => w.unmount()))
describe('人员确认操作组件', () => {
  it('拒绝必须有意见，批准只发送本人的决定', async () => {
    const w = setup()
    await w.get('[data-testid="change-reject"]').trigger('click')
    const body = new DOMWrapper(document.body)
    expect(
      body.get('[data-testid="reject-confirm"]').attributes('disabled'),
    ).toBeDefined()
    expect(w.emitted('decision')).toBeUndefined()
    await body.get('textarea#reject-reason').setValue('本人与新时段冲突')
    await body.get('[data-testid="reject-confirm"]').trigger('click')
    expect(w.emitted('decision')).toEqual([
      ['confirm', { decision: 'REJECT', comment: '本人与新时段冲突' }],
    ])
    expect(w.find('[data-testid="approver-approve"]').exists()).toBe(false)
  })
  it('打开拒绝弹窗后状态失效不能提交，返回不发出决定', async () => {
    const w = setup()
    await w.get('[data-testid="change-reject"]').trigger('click')
    const body = new DOMWrapper(document.body)
    await body.get('textarea#reject-reason').setValue('时间不合适')
    await w.setProps({ disabled: true })
    expect(
      body.get('[data-testid="reject-confirm"]').attributes('disabled'),
    ).toBeDefined()
    await body.get('[data-testid="reject-confirm"]').trigger('click')
    expect(w.emitted('decision')).toBeUndefined()
  })
  it('开发预览禁用业务动作，不发出写事件', async () => {
    const w = setup(true)
    await w.get('[data-testid="staff-approve"]').trigger('click')
    expect(
      w.get('[data-testid="staff-approve"]').attributes('disabled'),
    ).toBeDefined()
    expect(w.emitted('decision')).toBeUndefined()
  })
})
