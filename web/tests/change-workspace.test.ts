import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { effectScope, nextTick } from 'vue'
import { api } from '../src/api'
import { ApiError } from '../src/api/client'
import { useChangeWorkspace } from '../src/composables/useChangeWorkspace'
import type { ChangePreview, Plan, ChangeView } from '../src/types/api'
import { actor, activity, resources } from './fixtures'
vi.mock('../src/api', () => ({
  api: {
    activity: vi.fn(),
    resources: vi.fn(),
    changePreview: vi.fn(),
    createChange: vi.fn(),
  },
}))
const envelope = <T>(data: T) => ({ data, requestId: 'test' })
const a = () => activity({ activeChangeId: null })
function previewResult(plan: Plan): ChangePreview {
  return {
    check: { feasible: true, conflicts: [] },
    alternatives: {
      items: [
        {
          plan: {
            ...plan,
            startAt: '2030-10-20T12:00:00+08:00',
            endAt: '2030-10-20T13:00:00+08:00',
          },
          timeShiftMinutes: 120,
          venueChanged: false,
          replacedEquipmentLines: 0,
          explanation: '示例测试候选',
        },
      ],
      truncated: false,
      evaluatedCount: 3,
      offsetsMinutes: [0, 120],
      maxEvaluations: 300,
    },
    impact: {
      oldPlan: a().input.plan,
      newPlan: plan,
      releasedResourceIds: [],
      acquiredResourceIds: [],
      retainedResourceIds: [],
      requiredStaffUserIds: [],
      registeredUserIds: [],
      registeredCount: 12,
      notificationUserIds: [],
      approvalReset: true,
      originalResourceRisk: 'NORMAL',
      changedFields: ['startAt', 'endAt'],
    },
  }
}
const scopes: ReturnType<typeof effectScope>[] = []
function setup() {
  const scope = effectScope()
  scopes.push(scope)
  return scope.run(() =>
    useChangeWorkspace('activity-1', () => actor({ id: a().ownerId }), vi.fn()),
  )!
}
beforeEach(() => {
  vi.resetAllMocks()
  vi.mocked(api.activity).mockResolvedValue(envelope(a()))
  vi.mocked(api.resources).mockResolvedValue(envelope(resources))
  vi.mocked(api.changePreview).mockImplementation(async (_, p) =>
    envelope(previewResult(structuredClone(p.targetPlan))),
  )
})
afterEach(() => {
  scopes.splice(0).forEach((s) => s.stop())
})
describe('改期工作台真实请求编排（单元替身）', () => {
  it('无预览不能创建；选择候选必须重新预览最终目标', async () => {
    const s = setup()
    await s.load()
    s.reason.value = '时间调整'
    await s.create()
    expect(api.createChange).not.toHaveBeenCalled()
    await s.preview()
    expect(s.ready.value).toBe(true)
    const target = structuredClone(s.result.value!.alternatives.items[0].plan)
    await s.select(0)
    expect(api.changePreview).toHaveBeenCalledTimes(2)
    expect(vi.mocked(api.changePreview).mock.calls[1][1].targetPlan).toEqual(
      target,
    )
    expect(s.result.value!.impact.newPlan).toEqual(target)
    expect(s.ready.value).toBe(true)
  })
  it('编辑目标使旧影响失效，迟到响应也不能恢复创建按钮', async () => {
    const s = setup()
    await s.load()
    s.reason.value = '调整'
    let resolve!: (r: ReturnType<typeof envelope<ChangePreview>>) => void
    vi.mocked(api.changePreview).mockImplementationOnce(
      () =>
        new Promise((r) => {
          resolve = r
        }),
    )
    const old: Plan = JSON.parse(JSON.stringify(s.target.value!)),
      request = s.preview()
    s.target.value!.venueId = 'other-venue'
    await nextTick()
    resolve(envelope(previewResult(old)))
    await request
    expect(s.result.value).toBeNull()
    expect(s.ready.value).toBe(false)
  })
  it('409后保留目标/原因，加载新版本且要求重新预览', async () => {
    const s = setup()
    await s.load()
    s.reason.value = '保留输入'
    await s.preview()
    const target: Plan = JSON.parse(JSON.stringify(s.target.value!))
    vi.mocked(api.createChange).mockReturnValue({
      key: '00000000-0000-4000-8000-000000000001',
      run: vi
        .fn()
        .mockRejectedValue(new ApiError(409, 'STALE_VERSION', '版本已变化')),
    })
    vi.mocked(api.activity).mockResolvedValue(
      envelope(activity({ versionNo: 4, lockVersion: 9 })),
    )
    await s.create()
    expect(s.target.value).toEqual(target)
    expect(s.reason.value).toBe('保留输入')
    expect(s.activity.value!.lockVersion).toBe(9)
    expect(s.ready.value).toBe(false)
    await s.preview()
    expect(s.ready.value).toBe(true)
  })
  it('创建结果未知时只重放原命令，不使用新版本另建申请', async () => {
    const s = setup()
    await s.load()
    s.reason.value = '调整'
    await s.preview()
    const run = vi
      .fn()
      .mockRejectedValueOnce(new ApiError(0, 'NETWORK_ERROR', '超时'))
      .mockResolvedValue(envelope({ id: 'created' } as ChangeView))
    vi.mocked(api.createChange).mockReturnValue({
      key: '00000000-0000-4000-8000-000000000002',
      run,
    })
    await s.create()
    expect(s.mutation.uncertain.value).toBe(true)
    await s.create()
    expect(api.createChange).toHaveBeenCalledOnce()
    await s.mutation.execute()
    expect(run).toHaveBeenCalledTimes(2)
    expect(api.createChange).toHaveBeenCalledOnce()
    expect(vi.mocked(api.createChange).mock.calls[0][1]).toMatchObject({
      expectedLockVersion: 7,
      baseVersionNo: 3,
      reason: '调整',
    })
  })
  it('预览目标不一致或不可行不能创建', async () => {
    const s = setup()
    await s.load()
    s.reason.value = '调整'
    const wrong = previewResult({ ...a().input.plan, venueId: 'other' })
    vi.mocked(api.changePreview).mockResolvedValueOnce(envelope(wrong))
    await s.preview()
    expect(s.previewError.value).toBeTruthy()
    expect(s.ready.value).toBe(false)
    const blocked = previewResult(a().input.plan)
    blocked.check.feasible = false
    vi.mocked(api.changePreview).mockResolvedValueOnce(envelope(blocked))
    await s.preview()
    expect(s.ready.value).toBe(false)
  })
})
