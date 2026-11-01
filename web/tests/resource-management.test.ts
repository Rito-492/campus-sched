import { afterEach, expect, it } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import ElementPlus, { ElPopconfirm } from 'element-plus'
import ResourceManagePanel from '../src/components/ResourceManagePanel.vue'
import { actor, resources } from './fixtures'

let wrapper: ReturnType<typeof mount> | undefined
afterEach(() => wrapper?.unmount())

it.each([true, false])(
  '刷新同一资源后显示并保存最新申请资格（原值 %s）',
  async (before) => {
    const resource = {
      ...resources[0],
      allowTeacher: before,
      allowStudent: before,
    }
    wrapper = mount(ResourceManagePanel, {
      props: {
        actor: actor({
          roles: ['ADMIN'],
          permissions: ['RESOURCE_MANAGE'],
          managedResourceIds: [resource.id],
        }),
        resources: [resource],
        faults: [],
        revision: 0,
      },
      global: {
        plugins: [ElementPlus],
        components: { RouterLink: RouterLinkStub },
      },
    })
    await flushPromises()
    const teacher = () =>
      wrapper!.get('[data-testid="eligibility-teacher"] input')
        .element as HTMLInputElement
    const student = () =>
      wrapper!.get('[data-testid="eligibility-student"] input')
        .element as HTMLInputElement
    expect(teacher().checked).toBe(before)
    expect(student().checked).toBe(before)
    // 手动刷新仅替换资源快照，不改变ID或写入成功后的revision。
    await wrapper.setProps({
      resources: [
        { ...resource, allowTeacher: !before, allowStudent: !before },
      ],
    })
    await flushPromises()
    expect(teacher().checked).toBe(!before)
    expect(student().checked).toBe(!before)
    wrapper
      .findAllComponents(ElPopconfirm)
      .find((c) => c.props('title')?.includes('申请资格'))!
      .vm.$emit('confirm', new MouseEvent('click'))
    await flushPromises()
    expect(wrapper.emitted('eligibility')).toEqual([
      [resource.id, { allowTeacher: !before, allowStudent: !before }],
    ])
  },
)

it('进入加载状态不会提前覆盖未保存的资格输入', async () => {
  const resource = { ...resources[0], allowTeacher: true }
  wrapper = mount(ResourceManagePanel, {
    props: {
      actor: actor({
        roles: ['ADMIN'],
        permissions: ['RESOURCE_MANAGE'],
        managedResourceIds: [resource.id],
      }),
      resources: [resource],
      faults: [],
    },
    global: {
      plugins: [ElementPlus],
      components: { RouterLink: RouterLinkStub },
    },
  })
  await flushPromises()
  await wrapper.get('[data-testid="eligibility-teacher"] input').setValue(false)
  await wrapper.setProps({ disabled: true })
  await wrapper.setProps({ disabled: false })
  expect(
    (
      wrapper.get('[data-testid="eligibility-teacher"] input')
        .element as HTMLInputElement
    ).checked,
  ).toBe(false)
})
