import { describe, expect, it, vi } from 'vitest'
import { useMutation } from '../src/composables/useMutation'
import { ApiError } from '../src/api/client'
describe('持久操作结果与重试', () => {
  it('网络未知结果锁定其他命令，并复用原命令重试', async () => {
    const run = vi
      .fn()
      .mockRejectedValueOnce(new ApiError(0, 'NETWORK_ERROR', '连接中断'))
      .mockResolvedValue({ data: 'done', requestId: 'r' })
    const next = { run: vi.fn() },
      confirmed = vi.fn(),
      m = useMutation({ confirmed })
    await m.execute({ run })
    expect(m.uncertain.value).toBe(true)
    await m.execute(next)
    expect(next.run).not.toHaveBeenCalled()
    await m.execute()
    expect(run).toHaveBeenCalledTimes(2)
    expect(confirmed).toHaveBeenCalledWith('done')
    expect(m.locked.value).toBe(false)
  })
  it('写入已确认后，刷新失败不能重放该写入', async () => {
    const run = vi.fn().mockResolvedValue({ data: 1 }),
      refresh = new Error('查询断开')
    const m = useMutation({
      confirmed: async () => {
        throw refresh
      },
    })
    await m.execute({ run })
    await m.execute()
    expect(run).toHaveBeenCalledTimes(1)
    expect(m.uncertain.value).toBe(false)
    expect(m.refreshError.value).toBe(refresh)
    expect(m.message.value).toContain('确认')
  })
  it('409重新查询且不把查询失败当成写入未知', async () => {
    const conflict = vi.fn().mockRejectedValue(new Error('重新查询失败'))
    const m = useMutation({ conflict }),
      run = vi
        .fn()
        .mockRejectedValue(new ApiError(409, 'STALE_VERSION', '版本冲突'))
    await m.execute({ run })
    await m.execute()
    expect(conflict).toHaveBeenCalledOnce()
    expect(run).toHaveBeenCalledOnce()
    expect(m.locked.value).toBe(false)
    expect(m.refreshError.value).toBeTruthy()
  })
  it('提交中拦截重复点击，服务端明确拒绝后可修正再提交', async () => {
    let reject!: (e: unknown) => void
    const run = vi.fn(
        () =>
          new Promise<never>((_, r) => {
            reject = r
          }),
      ),
      m = useMutation()
    const first = m.execute({ run })
    await m.execute({ run })
    expect(run).toHaveBeenCalledOnce()
    reject(new ApiError(403, 'FORBIDDEN', '权限变更'))
    await first
    const next = { run: vi.fn().mockResolvedValue({ data: 1 }) }
    await m.execute(next)
    expect(next.run).toHaveBeenCalledOnce()
  })
})
