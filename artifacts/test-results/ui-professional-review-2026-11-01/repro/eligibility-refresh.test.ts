import { afterEach, expect, it } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ResourceManagePanel from '../../src/components/ResourceManagePanel.vue'
import { actor, resources } from '../../tests/fixtures'

let wrapper: ReturnType<typeof mount> | undefined
afterEach(() => wrapper?.unmount())

it('刷新已选资源后，申请资格表单必须显示本次服务器返回的开关值', async () => {
  const before = { ...resources[0], allowTeacher: true, allowStudent: true }
  wrapper = mount(ResourceManagePanel, {
    props: {
      actor: actor({ roles: ['ADMIN'], permissions: ['RESOURCE_MANAGE'], managedResourceIds: [before.id] }),
      resources: [before], faults: [], revision: 0,
    },
    global: { plugins: [ElementPlus], components: { RouterLink: RouterLinkStub } },
  })
  await flushPromises()
  const teacher = () => wrapper!.get('[data-testid="eligibility-teacher"] input').element as HTMLInputElement
  const student = () => wrapper!.get('[data-testid="eligibility-student"] input').element as HTMLInputElement
  expect(teacher().checked).toBe(true)
  expect(student().checked).toBe(true)
  // 对应 ResourceManagePage.load 的手动刷新：资源数组更新，revision 不变。
  await wrapper.setProps({ resources: [{ ...before, allowTeacher: false, allowStudent: false }] })
  await flushPromises()
  expect.soft(teacher().checked, '服务端关闭教师资格后仍显示勾选').toBe(false)
  expect.soft(student().checked, '服务端关闭学生资格后仍显示勾选').toBe(false)
})
