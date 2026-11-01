import { afterEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import ActivityDetailPage from '../src/pages/ActivityDetailPage.vue'
import { useSession } from '../src/stores/session'
import { api } from '../src/api'
import { ApiError } from '../src/api/client'
import type { Actor, ActivityView } from '../src/types/api'
import { activity, actor, resources } from './fixtures'
vi.mock('../src/api', () => ({
  api: {
    activity: vi.fn(),
    resources: vi.fn(),
    preview: vi.fn(),
    alternatives: vi.fn(),
    staff: vi.fn(),
    editActivity: vi.fn(),
    reserve: vi.fn(),
  },
}))
let wrapper: ReturnType<typeof mount> | undefined
afterEach(() => {
  wrapper?.unmount()
  document.body.innerHTML = ''
  vi.resetAllMocks()
})
async function openDetail(a: ActivityView, currentActor: Actor) {
  vi.mocked(api.activity).mockResolvedValue({ data: a, requestId: 'r' })
  vi.mocked(api.resources).mockResolvedValue({
    data: resources,
    requestId: 'r',
  })
  vi.mocked(api.staff).mockResolvedValue({ data: [], requestId: 'r' })
  const pinia = createPinia()
  setActivePinia(pinia)
  useSession().actor = currentActor
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/activities/:id', component: ActivityDetailPage },
      { path: '/:pathMatch(.*)*', component: { template: '<div />' } },
    ],
  })
  await router.push(`/activities/${a.id}`)
  await router.isReady()
  wrapper = mount(RouterView, {
    attachTo: document.body,
    global: { plugins: [pinia, router, ElementPlus] },
  })
  await flushPromises()
  return wrapper
}

it.each([401, 403, 404])(
  '刷新返回 %i 后移除旧活动及关联记录，重试可恢复',
  async (status) => {
    const a = activity()
    a.input.title = '仅内部可读的活动'
    a.input.description = '受保护的活动说明'
    a.input.staffUserIds = ['staff-private']
    const w = await openDetail(a, actor({ id: a.ownerId }))
    expect(w.text()).toContain(a.input.title)
    expect(w.get('[data-testid="activity-records"]').text()).toContain(
      'staff-private',
    )
    vi.mocked(api.activity).mockRejectedValueOnce(
      new ApiError(status, 'FORBIDDEN', '无法读取活动'),
    )
    await w.get('[data-testid="activity-refresh"]').trigger('click')
    await flushPromises()
    expect(w.get('[data-testid="api-error"]').text()).toContain('无法读取活动')
    for (const content of [
      a.input.title,
      a.input.description,
      'staff-private',
      resources[0].name,
    ])
      expect(w.text()).not.toContain(content)
    for (const testId of [
      'activity-records',
      'registration-submit',
      'current-version',
      'activity-stale',
    ])
      expect(w.find(`[data-testid="${testId}"]`).exists()).toBe(false)
    await w.get('[data-testid="api-error"] button').trigger('click')
    await flushPromises()
    expect(w.text()).toContain(a.input.title)
    expect(w.find('[data-testid="activity-records"]').exists()).toBe(true)
  },
)

it('网络刷新失败时明确标记旧快照并禁止办理，恢复后移除提示', async () => {
  const a = activity({ status: 'DRAFT' })
  const w = await openDetail(a, actor({ id: a.ownerId }))
  vi.mocked(api.activity).mockRejectedValueOnce(
    new ApiError(0, 'NETWORK_ERROR', '网络中断'),
  )
  await w.get('[data-testid="activity-refresh"]').trigger('click')
  await flushPromises()
  expect(w.text()).toContain(a.input.title)
  expect(w.get('[data-testid="activity-stale"]').text()).toContain('旧快照')
  expect(
    w.get('[data-testid="activity-submit"]').attributes('disabled'),
  ).toBeDefined()
  expect(w.find('[data-testid="activity-records"]').exists()).toBe(false)
  await w.get('[data-testid="activity-refresh"]').trigger('click')
  await flushPromises()
  expect(w.find('[data-testid="activity-stale"]').exists()).toBe(false)
  expect(
    w.get('[data-testid="activity-submit"]').attributes('disabled'),
  ).toBeUndefined()
})

it('联合预约 409 重读完成后保留结构化冲突，下一次成功后清除', async () => {
  const a = activity({
    status: 'APPROVED_UNRESERVED',
    effectiveVersionNo: null,
  })
  const w = await openDetail(
    a,
    actor({
      roles: ['ADMIN'],
      permissions: ['RESOURCE_CONFIRM'],
      managedResourceIds: resources.map((r) => r.id),
    }),
  )
  const conflict = {
    code: 'EQUIPMENT_SHORTAGE',
    resourceId: 'equipment-1',
    resourceName: '测试设备',
    startsAt: a.input.plan.startAt,
    endsAt: a.input.plan.endAt,
    required: 2,
    available: 1,
    message: '同时用量不足',
  }
  const run = vi.fn().mockRejectedValueOnce(
    new ApiError(409, 'RESOURCE_CONFLICT', '资源冲突', 'r', {
      conflicts: [conflict],
    }),
  )
  vi.mocked(api.reserve).mockReturnValue({
    key: '00000000-0000-4000-8000-000000000001',
    run,
  })
  let finishRefresh!: (value: { data: ActivityView; requestId: string }) => void
  vi.mocked(api.activity).mockReturnValueOnce(
    new Promise((resolve) => {
      finishRefresh = resolve
    }),
  )
  await w.get('[data-testid="resource-confirm"]').trigger('click')
  await flushPromises()
  expect(
    w.get('[data-testid="resource-confirm"]').attributes('disabled'),
  ).toBeDefined()
  finishRefresh({ data: { ...a, lockVersion: 8 }, requestId: 'r' })
  await flushPromises()
  expect(w.get('[data-testid="conflict-list"]').text()).toContain(
    '同时用量不足',
  )
  expect(w.get('[data-testid="conflict-list"]').text()).toContain(
    '需要 2 / 可用 1',
  )
  run.mockResolvedValueOnce({
    data: { ...a, status: 'SCHEDULED' },
    requestId: 'r',
  })
  vi.mocked(api.activity).mockResolvedValueOnce({
    data: { ...a, status: 'SCHEDULED' },
    requestId: 'r',
  })
  await w.get('[data-testid="resource-confirm"]').trigger('click')
  await flushPromises()
  expect(w.find('[data-testid="conflict-list"]').exists()).toBe(false)
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

it('资源查询先失败不能掩盖稍后返回的活动403', async () => {
  const a = activity()
  const w = await openDetail(a, actor({ id: a.ownerId }))
  let deny!: (e: unknown) => void
  vi.mocked(api.activity).mockReturnValueOnce(
    new Promise((_, reject) => {
      deny = reject
    }),
  )
  vi.mocked(api.resources).mockRejectedValueOnce(
    new ApiError(0, 'NETWORK_ERROR', '资源网络中断'),
  )
  await w.get('[data-testid="activity-refresh"]').trigger('click')
  await flushPromises()
  deny(new ApiError(403, 'FORBIDDEN', '详情权限已撤销'))
  await flushPromises()
  expect(w.text()).not.toContain(a.input.title)
  expect(w.find('[data-testid="activity-records"]').exists()).toBe(false)
  expect(w.get('[data-testid="api-error"]').text()).toContain('详情权限已撤销')
})

it('409刷新和手动重试交错时，旧成功响应不能恢复已被403清除的详情', async () => {
  const a = activity({ status: 'APPROVED_UNRESERVED' })
  const w = await openDetail(
    a,
    actor({
      roles: ['ADMIN'],
      permissions: ['RESOURCE_CONFIRM'],
      managedResourceIds: resources.map((r) => r.id),
    }),
  )
  vi.mocked(api.reserve).mockReturnValue({
    key: '00000000-0000-4000-8000-000000000001',
    run: vi
      .fn()
      .mockRejectedValue(new ApiError(409, 'STALE_VERSION', '请刷新版本')),
  })
  let oldResponse!: (value: { data: ActivityView; requestId: string }) => void
  vi.mocked(api.activity).mockReturnValueOnce(
    new Promise((resolve) => {
      oldResponse = resolve
    }),
  )
  await w.get('[data-testid="resource-confirm"]').trigger('click')
  await flushPromises()
  vi.mocked(api.activity).mockRejectedValueOnce(
    new ApiError(403, 'FORBIDDEN', '权限已撤销'),
  )
  await w.get('.mutation-error button').trigger('click')
  await flushPromises()
  oldResponse({ data: a, requestId: 'old' })
  await flushPromises()
  expect(w.text()).not.toContain(a.input.title)
  expect(w.find('[data-testid="activity-records"]').exists()).toBe(false)
})
