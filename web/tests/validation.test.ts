import { describe, expect, it } from 'vitest'
import { validateActivity } from '../src/domain/validation'
import { activity } from './fixtures'
describe('创建活动前校验', () => {
  const now = Date.parse('2030-10-19T00:00:00Z')
  it('合法表单通过', () =>
    expect(validateActivity(activity().input, now)).toEqual([]))
  it('检测容量、时长、布撤场和空标题', () => {
    const input = activity().input
    input.title = ' '
    input.registrationCapacity = 31
    input.plan.endAt = input.plan.startAt
    input.plan.setupMinutes = 121
    expect(validateActivity(input, now)).toHaveLength(4)
  })
  it('检测布场跨上海自然日', () => {
    const input = activity().input
    input.plan.startAt = '2030-10-20T00:15:00+08:00'
    input.plan.endAt = '2030-10-20T01:00:00+08:00'
    expect(validateActivity(input, now)).toContain(
      '含布撤场的占用必须在同一上海自然日内',
    )
  })
  it('设备重复及非整数数量拒绝', () => {
    const input = activity().input
    input.plan.equipment.push({ ...input.plan.equipment[0], quantity: 1.5 })
    expect(validateActivity(input, now)).toContain(
      '设备不能重复，数量须为正整数',
    )
  })
  it('无效时间不会抛RangeError', () => {
    const input = activity().input
    input.plan.startAt = 'invalid'
    expect(validateActivity(input, now).length).toBeGreaterThan(0)
  })
})
