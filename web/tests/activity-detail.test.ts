import { afterEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import ActivityDetailPage from '../src/pages/ActivityDetailPage.vue'
import { useSession } from '../src/stores/session'
import { api } from '../src/api'
import { activity, actor, resources } from './fixtures'
vi.mock('../src/api', () => ({
  api: {
    activity: vi.fn(),
    resources: vi.fn(),
    preview: vi.fn(),
    alternatives: vi.fn(),
    staff: vi.fn(),
    editActivity: vi.fn(),
  },
}))
let wrapper: ReturnType<typeof mount> | undefined
afterEach(() => {
  wrapper?.unmount()
  document.body.innerHTML = ''
  vi.resetAllMocks()
})
it('正式详情中候选只填入草稿，显式保存后才更新当前安排', async () => {
  const a = activity({ status: 'DRAFT', effectiveVersionNo: null }),
    envelope = <T>(data: T) => ({ data, requestId: 'r' })
  const target = {
    ...a.input.plan,
    startAt: '2030-10-20T12:00:00+08:00',
    endAt: '2030-10-20T13:00:00+08:00',
  }
  vi.mocked(api.activity).mockResolvedValue(envelope(a))
  vi.mocked(api.resources).mockResolvedValue(envelope(resources))
  vi.mocked(api.staff).mockResolvedValue(envelope([]))
  vi.mocked(api.preview).mockResolvedValue(
    envelope({ feasible: false, conflicts: [] }),
  )
  vi.mocked(api.alternatives).mockResolvedValue(
    envelope({
      items: [
        {
          plan: target,
          timeShiftMinutes: 120,
          venueChanged: false,
          replacedEquipmentLines: 0,
          explanation: '测试候选',
        },
      ],
      evaluatedCount: 1,
      truncated: false,
      offsetsMinutes: [120],
      maxEvaluations: 300,
    }),
  )
  const updated = {
    ...a,
    lockVersion: 8,
    versionNo: 4,
    input: { ...a.input, plan: target },
  }
  vi.mocked(api.editActivity).mockReturnValue({
    key: '00000000-0000-4000-8000-000000000001',
    run: vi.fn().mockResolvedValue(envelope(updated)),
  })
  const pinia = createPinia()
  setActivePinia(pinia)
  useSession().actor = actor({ id: a.ownerId })
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/activities', component: { template: '<div>活动列表</div>' } },
      { path: '/activities/:id', component: ActivityDetailPage },
    ],
  })
  await router.push(`/activities/${a.id}`)
  await router.isReady()
  wrapper = mount(RouterView, {
    attachTo: document.body,
    global: { plugins: [pinia, router, ElementPlus] },
  })
  await flushPromises()
  await wrapper.get('[data-testid="activity-preview"]').trigger('click')
  await flushPromises()
  await wrapper.get('[data-testid="alternative-0"]').trigger('click')
  await flushPromises()
  expect(api.editActivity).not.toHaveBeenCalled()
  expect(
    (wrapper.get('[data-testid="draft-start"]').element as HTMLInputElement)
      .value,
  ).toBe('2030-10-20T12:00')
  await wrapper.get('[data-testid="draft-save"]').trigger('click')
  await flushPromises()
  expect(vi.mocked(api.editActivity).mock.calls[0][1]).toMatchObject({
    expectedLockVersion: 7,
    input: { plan: target },
  })
  expect(wrapper.find('[data-testid="draft-editor"]').exists()).toBe(false)
  expect(wrapper.find('[data-testid="alternative-0"]').exists()).toBe(false)
  expect(wrapper.text()).toContain('10/20 12:00')
})
