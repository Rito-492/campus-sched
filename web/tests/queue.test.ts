import { describe, expect, it } from 'vitest'
import { mapSettled } from '../src/domain/queue'
describe('待办关联查询', () => {
  it('最多6并发，单项失败不掩盖其余记录', async () => {
    let running = 0,
      maximum = 0
    const result = await mapSettled(
      Array.from({ length: 31 }, (_, i) => i),
      async (i) => {
        running++
        maximum = Math.max(maximum, running)
        await Promise.resolve()
        running--
        if (i === 8) throw new Error('一个活动不可读')
        return i
      },
    )
    expect(maximum).toBe(6)
    expect(result[8].status).toBe('rejected')
    expect(result[30]).toEqual({ status: 'fulfilled', value: 30 })
  })
  it('查询失效后不再继续调度剩余活动', async () => {
    let active = true,
      started = 0
    await mapSettled(
      Array.from({ length: 30 }, (_, i) => i),
      async (i) => {
        started++
        await Promise.resolve()
        active = false
        return i
      },
      () => active,
    )
    expect(started).toBe(6)
  })
})
