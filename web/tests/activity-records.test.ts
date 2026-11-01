import { afterEach, expect, it, vi } from 'vitest'
import { flushPromises, mount, RouterLinkStub } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ActivityRecords from '../src/components/ActivityRecords.vue'
import { api } from '../src/api'
import { ApiError } from '../src/api/client'
import { activity, actor, resources } from './fixtures'
import type { Actor, ActivityView, ChangeView } from '../src/types/api'

vi.mock('../src/api', () => ({ api: { staff: vi.fn(), changes: vi.fn() } }))
let wrapper: ReturnType<typeof mount> | undefined
afterEach(() => {
  wrapper?.unmount()
  vi.resetAllMocks()
})
const a = activity()
a.input.staffUserIds = ['staff-1', 'staff-disabled']
const staff = {
  id: 'staff-1',
  displayName: '本组织工作人员',
  organizationId: a.organizationId,
  enabled: true,
}
function show(currentActor: Actor | null, currentActivity: ActivityView = a) {
  wrapper = mount(ActivityRecords, {
    props: { activity: currentActivity, actor: currentActor, resources },
    global: {
      plugins: [ElementPlus],
      components: { RouterLink: RouterLinkStub },
    },
  })
  return wrapper
}

it.each([
  [
    '纯批准人',
    actor({
      roles: ['APPROVER'],
      approvalScopes: [
        {
          organizationId: a.organizationId,
          resourceCategories: ['VENUE', 'PROJECTOR'],
        },
      ],
    }),
  ],
  [
    '纯资源复核管理员',
    actor({
      roles: ['ADMIN'],
      permissions: ['RESOURCE_CONFIRM'],
      managedResourceIds: resources.map((r) => r.id),
    }),
  ],
  [
    '跨组织兼任批准人',
    actor({
      roles: ['APPROVER', 'APPLICANT'],
      organizationId: 'other-org',
      approvalScopes: [
        {
          organizationId: a.organizationId,
          resourceCategories: ['VENUE', 'PROJECTOR'],
        },
      ],
    }),
  ],
  ['停用申请人', actor({ enabled: false })],
] as const)('%s 不查询候选接口，仍显示授权人员 ID', async (_, currentActor) => {
  const w = show(currentActor)
  await flushPromises()
  expect(api.staff).not.toHaveBeenCalled()
  expect(w.find('[data-testid="api-error"]').exists()).toBe(false)
  expect(w.get('[data-testid="activity-staff-staff-1"]').text()).toBe('staff-1')
  expect(w.get('[data-testid="staff-name-unavailable"]').text()).toContain(
    '人员 ID',
  )
})

it('同组织申请人只用候选名单补充已有人员姓名，未返回或停用人员仍保留 ID', async () => {
  vi.mocked(api.staff).mockResolvedValue({
    data: [staff, { ...staff, id: 'unrelated', displayName: '无关候选人' }],
    requestId: 'r',
    truncated: true,
  })
  const w = show(actor())
  await flushPromises()
  expect(api.staff).toHaveBeenCalledTimes(1)
  expect(w.get('[data-testid="activity-staff-staff-1"]').text()).toBe(
    staff.displayName,
  )
  expect(w.get('[data-testid="activity-staff-staff-disabled"]').text()).toBe(
    'staff-disabled',
  )
  expect(w.text()).not.toContain('无关候选人')
  expect(w.text()).toContain('结果已截断')
})

it('姓名查询失败不遮住已授权的人员 ID', async () => {
  vi.mocked(api.staff).mockRejectedValue(
    new ApiError(0, 'NETWORK_ERROR', '网络中断'),
  )
  const w = show(actor())
  await flushPromises()
  expect(w.get('[data-testid="api-error"]').text()).toContain('网络中断')
  expect(w.get('[data-testid="activity-staff-staff-1"]').text()).toBe('staff-1')
})

it('角色失去候选查询资格后，迟到的姓名响应不能覆盖 ID 降级展示', async () => {
  let finish!: (r: { data: (typeof staff)[]; requestId: string }) => void
  vi.mocked(api.staff).mockReturnValue(
    new Promise((resolve) => {
      finish = resolve
    }),
  )
  const w = show(actor())
  await w.setProps({ actor: actor({ roles: ['APPROVER'] }) })
  finish({ data: [staff], requestId: 'r' })
  await flushPromises()
  expect(api.staff).toHaveBeenCalledTimes(1)
  expect(w.get('[data-testid="activity-staff-staff-1"]').text()).toBe('staff-1')
  expect(w.text()).not.toContain(staff.displayName)
})

function history(status: ChangeView['status']): ChangeView {
  return {
    id: 'history-1',
    activityId: a.id,
    baseVersionNo: a.versionNo,
    targetPlan: a.input.plan,
    reason: '工作人员可读的历史原因',
    status,
    impact: {
      oldPlan: a.input.plan,
      newPlan: a.input.plan,
      releasedResourceIds: [],
      acquiredResourceIds: [],
      retainedResourceIds: [],
      requiredStaffUserIds: ['staff-1'],
      registeredUserIds: [],
      registeredCount: 1,
      notificationUserIds: [],
      approvalReset: true,
      originalResourceRisk: 'NORMAL',
      changedFields: [],
    },
    confirmations: [],
    approverDecision: null,
    resourceDecision: null,
    lastConflicts: [],
    appliedVersionNo: status === 'APPLIED' ? 4 : null,
    createdBy: a.ownerId,
    createdAt: a.createdAt,
    updatedAt: a.createdAt,
  }
}
const redacted = () => ({
  ...a,
  activeChangeId: null,
  input: { ...a.input, staffUserIds: [] },
})
const historyTab = (w: ReturnType<typeof mount>) =>
  w.findAll('[role="tab"]').find((t) => t.text() === '改期记录')!

it.each([
  'AWAITING_CONFIRMATIONS',
  'APPLIED',
  'REJECTED',
  'WITHDRAWN',
] as const)(
  '人员数组被裁剪时，点击入口按API授权展示%s改期记录',
  async (status) => {
    const item = history(status)
    vi.mocked(api.changes).mockResolvedValue({
      data: [item],
      requestId: 'history-request',
    })
    const w = show(actor({ id: 'staff-1', identity: 'STUDENT' }), redacted())
    await flushPromises()
    expect(api.changes).not.toHaveBeenCalled()
    expect(api.staff).not.toHaveBeenCalled()
    await historyTab(w).trigger('click')
    await flushPromises()
    expect(api.changes).toHaveBeenCalledWith(a.id)
    expect(w.getComponent(RouterLinkStub).props('to')).toBe(
      '/changes/history-1',
    )
    expect(w.get('[data-testid="change-history"]').text()).toContain(
      item.reason,
    )
  },
)

it('普通查看者点击历史被拒绝时显示服务端原因，不冒充空列表', async () => {
  vi.mocked(api.changes).mockRejectedValue(
    new ApiError(
      403,
      'FORBIDDEN',
      '无权查看此活动的改期历史',
      'denied-request',
    ),
  )
  const w = show(actor({ identity: 'STUDENT' }), redacted())
  await flushPromises()
  await historyTab(w).trigger('click')
  await flushPromises()
  expect(w.get('[data-testid="api-error"]').text()).toContain(
    '无权查看此活动的改期历史',
  )
  expect(w.text()).toContain('denied-request')
  expect(w.find('[data-testid="change-history"]').exists()).toBe(false)
  expect(w.text()).not.toContain('暂无改期记录')
})

it('历史请求迟到时不能覆盖新账号的403或恢复旧记录', async () => {
  let resolve!: (value: { data: ChangeView[]; requestId: string }) => void
  vi.mocked(api.changes).mockReturnValueOnce(
    new Promise((done) => {
      resolve = done
    }),
  )
  const w = show(actor({ id: 'staff-1' }), redacted())
  await flushPromises()
  await historyTab(w).trigger('click')
  vi.mocked(api.changes).mockRejectedValueOnce(
    new ApiError(403, 'FORBIDDEN', '历史权限已撤销'),
  )
  await w.setProps({
    actor: actor({ id: 'another-user', identity: 'STUDENT' }),
  })
  await flushPromises()
  resolve({ data: [history('APPLIED')], requestId: 'old-request' })
  await flushPromises()
  expect(w.get('[data-testid="api-error"]').text()).toContain('历史权限已撤销')
  expect(w.text()).not.toContain('工作人员可读的历史原因')
  expect(w.find('[data-testid="change-record-history-1"]').exists()).toBe(false)
})
