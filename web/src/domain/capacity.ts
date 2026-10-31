import type { CalendarEntry, ResourceView } from '../types/api'
export interface CapacitySlice {
  startsAt: string
  endsAt: string
  used: number
  total: number
  unavailable: number
  available: number
  remaining: number
}
// 在每个端点应用净变化后计算下一个半开区间；相接预约不会被算作同时占用。
export function capacitySlices(
  resource: ResourceView,
  entries: CalendarEntry[],
  from: number,
  to: number,
): CapacitySlice[] {
  if (!Number.isFinite(from) || !Number.isFinite(to) || to <= from) return []
  const points = new Map<number, { used: number; unavailable: number }>([
    [from, { used: 0, unavailable: 0 }],
    [to, { used: 0, unavailable: 0 }],
  ])
  for (const e of entries.filter((e) => e.resourceId === resource.id)) {
    const start = Math.max(from, Date.parse(e.startsAt)),
      end = Math.min(to, Date.parse(e.endsAt))
    if (!Number.isFinite(start) || !Number.isFinite(end) || start >= end)
      continue
    const field = e.segment === 'FAULT' ? 'unavailable' : 'used'
    for (const [time, delta] of [
      [start, e.quantity],
      [end, -e.quantity],
    ]) {
      const point = points.get(time) ?? { used: 0, unavailable: 0 }
      point[field] += delta
      points.set(time, point)
    }
  }
  const times = [...points.keys()].sort((a, b) => a - b),
    slices: CapacitySlice[] = []
  let used = 0,
    unavailable = 0
  times.slice(0, -1).forEach((time, i) => {
    used += points.get(time)!.used
    unavailable += points.get(time)!.unavailable
    const available = Math.max(0, resource.quantity - unavailable)
    const last = slices.at(-1)
    if (last && last.used === used && last.unavailable === unavailable)
      last.endsAt = new Date(times[i + 1]).toISOString()
    else
      slices.push({
        startsAt: new Date(time).toISOString(),
        endsAt: new Date(times[i + 1]).toISOString(),
        used,
        total: resource.quantity,
        unavailable,
        available,
        remaining: Math.max(0, available - used),
      })
  })
  return slices
}
