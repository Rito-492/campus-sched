import { afterEach, expect, it, vi } from 'vitest'
import { mount, flushPromises } from '@vue/test-utils'
import { createPinia, setActivePinia } from 'pinia'
import { createMemoryHistory, createRouter, RouterView } from 'vue-router'
import ElementPlus from 'element-plus'
import ChangeDetailPage from '../src/pages/ChangeDetailPage.vue'
import { api } from '../src/api'
import { ApiError, tokenKey } from '../src/api/client'
import type { ChangeView } from '../src/types/api'
import { activity, actor, resources } from './fixtures'
vi.mock('../src/api', () => ({
  api: {
    me: vi.fn(),
    change: vi.fn(),
    activity: vi.fn(),
    resources: vi.fn(),
    approveChange: vi.fn(),
  },
}))
let wrapper: ReturnType<typeof mount> | undefined
afterEach(() => {
  wrapper?.unmount()
  document.body.innerHTML = ''
  sessionStorage.clear()
  vi.resetAllMocks()
})
async function openDetail() {
  const a = activity({ activeChangeId: 'change-1' })
  const c: ChangeView = {
    id: 'change-1',
    activityId: a.id,
    baseVersionNo: a.versionNo,
    targetPlan: a.input.plan,
    reason: '受保护的改期理由',
    status: 'PENDING_APPROVER',
    impact: {
      oldPlan: a.input.plan,
      newPlan: a.input.plan,
      releasedResourceIds: [],
      acquiredResourceIds: [],
      retainedResourceIds: [],
      requiredStaffUserIds: [],
      registeredUserIds: [],
      registeredCount: 12,
      notificationUserIds: [],
      approvalReset: true,
      originalResourceRisk: 'NORMAL',
      changedFields: [],
    },
    confirmations: [],
    approverDecision: null,
    resourceDecision: null,
    lastConflicts: [],
    appliedVersionNo: null,
    createdBy: a.ownerId,
    createdAt: a.createdAt,
    updatedAt: a.createdAt,
  }
  const currentActor = actor({
    roles: ['APPROVER'],
    approvalScopes: [
      {
        organizationId: a.organizationId,
        resourceCategories: ['VENUE', 'PROJECTOR'],
      },
    ],
  })
  vi.mocked(api.me).mockResolvedValue({ data: currentActor, requestId: 'r' })
  vi.mocked(api.activity).mockResolvedValue({ data: a, requestId: 'r' })
  vi.mocked(api.change).mockResolvedValue({ data: c, requestId: 'r' })
  vi.mocked(api.resources).mockResolvedValue({
    data: resources,
    requestId: 'r',
  })
  const pinia = createPinia()
  setActivePinia(pinia)
  sessionStorage.setItem(tokenKey, 'component-test-token')
  const router = createRouter({
    history: createMemoryHistory(),
    routes: [
      { path: '/changes/:id', component: ChangeDetailPage },
      { path: '/:pathMatch(.*)*', component: { template: '<div />' } },
    ],
  })
  await router.push('/changes/change-1')
  await router.isReady()
  wrapper = mount(RouterView, {
    attachTo: document.body,
    global: { plugins: [pinia, router, ElementPlus] },
  })
  await flushPromises()
  return { w: wrapper, c }
}
it('改期409自动刷新失权后，原始错误details也不能继续泄露受保护快照', async () => {
  const { w, c } = await openDetail()
  vi.mocked(api.approveChange).mockReturnValue({
    key: '00000000-0000-4000-8000-000000000001',
    run: vi
      .fn()
      .mockRejectedValue(
        new ApiError(409, 'STALE_VERSION', '版本冲突', 'r', { change: c }),
      ),
  })
  vi.mocked(api.change).mockRejectedValueOnce(
    new ApiError(403, 'FORBIDDEN', '改期读取权限已撤销'),
  )
  await w.get('[data-testid="approver-approve"]').trigger('click')
  await flushPromises()
  expect(w.text()).toContain('改期读取权限已撤销')
  expect(w.text()).not.toContain(c.reason)
  expect(w.find('[data-testid="change-impact"]').exists()).toBe(false)
  expect(w.find('[data-testid="mutation-error"]').exists()).toBe(false)
})
it('改期详情网络刷新失败时标明旧快照，并禁止处理', async () => {
  const { w, c } = await openDetail()
  vi.mocked(api.change).mockRejectedValueOnce(
    new ApiError(0, 'NETWORK_ERROR', '网络中断'),
  )
  await w.get('[data-testid="change-refresh"]').trigger('click')
  await flushPromises()
  expect(w.text()).toContain(c.reason)
  expect(w.get('[data-testid="change-detail-stale"]').text()).toContain(
    '旧快照',
  )
  expect(
    w.get('[data-testid="approver-approve"]').attributes('disabled'),
  ).toBeDefined()
})
