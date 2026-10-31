import type { ActivityInput, Plan } from '../types/api'
export function validateActivity(
  input: ActivityInput,
  now = Date.now(),
): string[] {
  const errors: string[] = []
  if (!input.title.trim() || input.title.length > 100)
    errors.push('活动名称须为1～100字')
  if (input.description.length > 2000) errors.push('活动简介不能超过2000字')
  if (
    !Number.isInteger(input.expectedAttendees) ||
    !Number.isInteger(input.registrationCapacity) ||
    input.registrationCapacity < 1 ||
    input.expectedAttendees < input.registrationCapacity
  )
    errors.push('预计人数须大于等于报名容量，且容量为正整数')
  return [...errors, ...validatePlan(input.plan, now)]
}
export function validatePlan(plan: Plan, now = Date.now()): string[] {
  const errors: string[] = []
  const start = Date.parse(plan.startAt),
    end = Date.parse(plan.endAt)
  if (
    !Number.isFinite(start) ||
    !Number.isFinite(end) ||
    start <= now ||
    end - start < 30 * 60000 ||
    end - start > 6 * 3600000
  )
    errors.push('请选择未来时间，活动时长为30分钟至6小时')
  if (
    Number.isFinite(start) &&
    Number.isFinite(end) &&
    (start % 60000 !== 0 || end % 60000 !== 0)
  )
    errors.push('活动时间须精确到分钟')
  if (
    ![plan.setupMinutes, plan.teardownMinutes].every(
      (n) => Number.isInteger(n) && n >= 0 && n <= 120,
    )
  )
    errors.push('布场及撤场须为0～120分钟')
  const day = (n: number) =>
    new Date(n + 8 * 3600000).toISOString().slice(0, 10)
  if (
    Number.isFinite(start) &&
    Number.isFinite(end) &&
    [plan.setupMinutes, plan.teardownMinutes].every(
      (n) => Number.isInteger(n) && n >= 0 && n <= 120,
    ) &&
    day(start - plan.setupMinutes * 60000) !==
      day(end + plan.teardownMinutes * 60000 - 1)
  )
    errors.push('含布撤场的占用必须在同一上海自然日内')
  if (!plan.venueId) errors.push('请选择活动场地')
  if (
    new Set(plan.equipment.map((e) => e.resourceId)).size !==
      plan.equipment.length ||
    plan.equipment.some(
      (e) => !e.resourceId || !Number.isInteger(e.quantity) || e.quantity < 1,
    )
  )
    errors.push('设备不能重复，数量须为正整数')
  return errors
}
