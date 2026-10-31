import { describe, expect, it } from 'vitest'
import { capacitySlices } from '../src/domain/capacity'
import type { CalendarEntry } from '../src/types/api'
import { resources } from './fixtures'
const time = (h: number) =>
  new Date(Date.UTC(2030, 9, 20) + h * 3600000).toISOString()
const entry = (
  start: number,
  end: number,
  quantity: number,
  segment: CalendarEntry['segment'] = 'EVENT',
): CalendarEntry => ({
  resourceId: 'equipment-1',
  startsAt: time(start),
  endsAt: time(end),
  quantity,
  segment,
  activityId: null,
  label: '已占用',
  risk: 'NORMAL',
})
describe('日历同时用量', () => {
  it('端点相接只取各段用量，不求不同时段总和', () => {
    const slices = capacitySlices(
      resources[1],
      [entry(1, 2, 2), entry(2, 3, 1)],
      Date.parse(time(1)),
      Date.parse(time(3)),
    )
    expect(slices.map((s) => s.used)).toEqual([2, 1])
    expect(Math.max(...slices.map((s) => s.used))).toBe(2)
  })
  it('重叠预约及多故障按半开区间计算，最低可用0且原预约仍显示', () => {
    const slices = capacitySlices(
      resources[1],
      [
        entry(1, 4, 1),
        entry(2, 3, 1),
        entry(2, 4, 1, 'FAULT'),
        entry(3, 4, 2, 'FAULT'),
      ],
      Date.parse(time(1)),
      Date.parse(time(4)),
    )
    expect(
      slices.map((s) => [s.used, s.unavailable, s.available, s.remaining]),
    ).toEqual([
      [1, 0, 2, 1],
      [2, 1, 1, 0],
      [1, 3, 0, 0],
    ])
  })
  it('布场、活动、撤场相接不重复累计，裁剪到可视区间', () => {
    const slices = capacitySlices(
      resources[1],
      [
        entry(0, 1, 2, 'SETUP'),
        entry(1, 2, 2),
        entry(2, 3, 2, 'TEARDOWN'),
        { ...entry(1, 3, 5), resourceId: 'other' },
      ],
      Date.parse(time(0.5)),
      Date.parse(time(2)),
    )
    expect(slices).toHaveLength(1)
    expect(slices[0].used).toBe(2)
    expect(slices[0].startsAt).toBe(time(0.5))
    expect(slices[0].endsAt).toBe(time(2))
  })
  it('无记录显示0占用，无效区间不制造时间块', () => {
    expect(
      capacitySlices(
        resources[1],
        [],
        Date.parse(time(1)),
        Date.parse(time(2)),
      )[0].used,
    ).toBe(0)
    expect(capacitySlices(resources[1], [], NaN, 0)).toEqual([])
  })
})
