import type {
  Actor,
  FaultInput,
  ResourceCreate,
  ResourceView,
} from '../types/api'
import { hasPermission } from './access'
export const mayManageResource = (actor: Actor | null, id: string) =>
  hasPermission(actor, 'RESOURCE_MANAGE') &&
  !!actor?.managedResourceIds.includes(id)
export function validateResource(r: ResourceCreate) {
  const errors: string[] = []
  if (!r.name.trim() || !r.category.trim()) errors.push('请填写资源名称和类别')
  if (!Number.isInteger(r.quantity) || r.quantity < 1)
    errors.push('库存须为正整数')
  if (
    r.kind === 'VENUE' &&
    (r.category !== 'VENUE' ||
      r.quantity !== 1 ||
      !Number.isInteger(r.capacity) ||
      (r.capacity ?? 0) < 1)
  )
    errors.push('场地类别须为VENUE、库存为1且容量为正整数')
  if (r.kind === 'EQUIPMENT' && r.capacity !== null)
    errors.push('设备不填写场地容量')
  if (
    ![r.openFrom, r.openTo].every((t) =>
      /^(?:[01]\d|2[0-3]):[0-5]\d$/.test(t),
    ) ||
    r.openFrom >= r.openTo
  )
    errors.push('开放时间须为同一天内的有效起止时间')
  return errors
}
export function validateFault(f: FaultInput, r: ResourceView) {
  const errors: string[] = []
  if (
    !Number.isFinite(Date.parse(f.startAt)) ||
    !Number.isFinite(Date.parse(f.endAt)) ||
    Date.parse(f.startAt) >= Date.parse(f.endAt)
  )
    errors.push('故障开始时间须早于结束时间')
  if (
    !Number.isInteger(f.unavailableQuantity) ||
    f.unavailableQuantity < 1 ||
    f.unavailableQuantity > r.quantity
  )
    errors.push('单条故障数量须为1至资源库存的整数')
  if (!f.reason.trim() || f.reason.length > 1000)
    errors.push('故障原因须为1～1000字')
  return errors
}
