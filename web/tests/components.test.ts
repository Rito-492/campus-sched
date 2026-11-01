import { describe, expect, it } from 'vitest'
import { mount } from '@vue/test-utils'
import ElementPlus from 'element-plus'
import ConflictList from '../src/components/ConflictList.vue'
import AlternativesPanel from '../src/components/AlternativesPanel.vue'
import ImpactPanel from '../src/components/ImpactPanel.vue'
import type { AlternativesView, Impact } from '../src/types/api'
import { activity } from './fixtures'
describe('核心交互组件', () => {
  it('开放时间冲突数量为空时不虚构缺口', () => {
    const wrapper = mount(ConflictList, {
      props: {
        conflicts: [
          {
            code: 'OUTSIDE_HOURS',
            resourceId: 'r',
            resourceName: '会议室',
            startsAt: activity().input.plan.startAt,
            endsAt: activity().input.plan.endAt,
            required: null,
            available: null,
            message: '超出开放时间',
          },
        ],
      },
    })
    expect(wrapper.text()).toContain('超出开放时间')
    expect(wrapper.text()).not.toContain('需要 null')
    expect(wrapper.find('[data-testid="conflict-list"]').exists()).toBe(true)
  })
  it('无方案及预算截断有明确说明', () => {
    const data: AlternativesView = {
      items: [],
      evaluatedCount: 300,
      maxEvaluations: 300,
      truncated: true,
      offsetsMinutes: [0],
    }
    const wrapper = mount(AlternativesPanel, {
      props: { alternatives: data },
      global: { plugins: [ElementPlus] },
    })
    expect(wrapper.text()).toContain('无可行方案')
    expect(wrapper.text()).toContain('搜索已达到预算')
  })
  it('选择方案只发出选择事件，不产生预约请求', async () => {
    const data: AlternativesView = {
      items: [
        {
          plan: activity().input.plan,
          timeShiftMinutes: 0,
          venueChanged: false,
          replacedEquipmentLines: 0,
          explanation: '原时间',
        },
      ],
      evaluatedCount: 1,
      maxEvaluations: 300,
      truncated: false,
      offsetsMinutes: [0],
    }
    const wrapper = mount(AlternativesPanel, {
      props: { alternatives: data },
      global: { plugins: [ElementPlus] },
    })
    await wrapper.get('[data-testid="alternative-0"]').trigger('click')
    expect(wrapper.emitted('select')).toEqual([[0]])
  })
  it('实时风险优先于创建快照，报名人数不从裁剪后的ID数组推算', () => {
    const plan = activity().input.plan
    const impact: Impact = {
      oldPlan: plan,
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
      changedFields: [],
    }
    const wrapper = mount(ImpactPanel, {
      props: { impact, currentRisk: 'AT_RISK' },
    })
    expect(wrapper.text()).toContain('12 人已报名')
    expect(wrapper.text()).toContain('原资源当前存在故障风险')
  })
})
