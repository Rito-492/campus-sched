import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { useUserAdmin } from '../src/composables/useUserAdmin'
import { actor, resources } from './fixtures'
import {
  emptyAuthorization,
  type UserOperation,
} from '../src/domain/authorization'
import type { Actor } from '../src/types/api'
const envelope = (data: unknown) =>
  new Response(
    JSON.stringify({ data, requestId: 'test-user-admin', truncated: false }),
    { status: 200 },
  )
describe('账号变更与真实请求客户端', () => {
  let current: Actor, fetcher: ReturnType<typeof vi.fn>
  const create = (): UserOperation => ({
    kind: 'create',
    input: {
      ...emptyAuthorization(),
      username: 'test-user',
      displayName: '测试用户',
      password: 'test-only-password',
      organizationId: 'org-1',
    },
  })
  const writes = () =>
    fetcher.mock.calls.filter(([, init]) => init.method !== 'GET')
  beforeEach(() => {
    current = actor({ roles: ['ADMIN'], permissions: ['USER_MANAGE'] })
    fetcher = vi.fn(async (url: string, init: RequestInit) => {
      if (init.method !== 'GET') return envelope(actor())
      return envelope(
        url.endsWith('/users')
          ? [actor()]
          : url.endsWith('/organizations')
            ? [{ id: 'org-1', name: '软件学院' }]
            : resources,
      )
    })
    vi.stubGlobal('fetch', fetcher)
  })
  afterEach(() => vi.unstubAllGlobals())
  it('无USER_MANAGE时读写均不发送请求', async () => {
    current = actor({ roles: ['ADMIN'], permissions: ['RESOURCE_MANAGE'] })
    const state = useUserAdmin(() => current, vi.fn())
    await state.load()
    await state.submit(create())
    expect(fetcher).not.toHaveBeenCalled()
    expect(state.ready.value).toBe(false)
    expect(state.actionError.value).toMatchObject({ status: 403 })
  })
  it('依赖查询失败时不展示可编辑账号，也不能创建', async () => {
    fetcher.mockRejectedValue(new TypeError('offline'))
    const state = useUserAdmin(() => current, vi.fn())
    await state.load()
    await state.submit(create())
    expect(state.ready.value).toBe(false)
    expect(state.users.value).toEqual([])
    expect(writes()).toHaveLength(0)
  })
  it('未知结果冻结编辑；重试保留同key、同请求体，不重复生成创建命令', async () => {
    const state = useUserAdmin(() => current, vi.fn())
    await state.load()
    fetcher.mockRejectedValueOnce(new TypeError('connection lost'))
    const operation = create()
    await state.submit(operation)
    expect(state.uncertain.value).toBe(true)
    if (operation.kind === 'create')
      operation.input.username = 'edited-after-timeout'
    await state.submit(operation)
    expect(writes()).toHaveLength(1)
    await state.submit()
    expect(writes()).toHaveLength(2)
    const first = writes()[0][1],
      second = writes()[1][1]
    expect(first.body).toBe(second.body)
    expect(first.headers['Idempotency-Key']).toBe(
      second.headers['Idempotency-Key'],
    )
    expect(JSON.parse(second.body).username).toBe('test-user')
    expect(state.completed.value).toBe(1)
    expect(state.uncertain.value).toBe(false)
  })
  it('写入成功但刷新失败，重试查询不会重发创建', async () => {
    const refresh = vi
      .fn()
      .mockRejectedValueOnce(new Error('me unavailable'))
      .mockResolvedValue(undefined)
    const state = useUserAdmin(() => current, refresh)
    await state.load()
    await state.submit(create())
    expect(state.completed.value).toBe(1)
    expect(state.ready.value).toBe(false)
    expect(state.message.value).toContain('服务端确认')
    await state.submit()
    expect(writes()).toHaveLength(1)
    await state.load()
    expect(refresh).toHaveBeenCalledTimes(2)
    expect(state.ready.value).toBe(true)
    expect(writes()).toHaveLength(1)
  })
  it('409保留服务器原因并刷新列表，解锁后由表单修正，不能悄悄重发', async () => {
    const state = useUserAdmin(() => current, vi.fn())
    await state.load()
    fetcher.mockResolvedValueOnce(
      new Response(
        JSON.stringify({
          error: { code: 'INVALID_STATE', message: '用户名已存在' },
          requestId: 'duplicate-user',
        }),
        { status: 409 },
      ),
    )
    await state.submit(create())
    expect(state.actionError.value).toMatchObject({
      status: 409,
      message: '用户名已存在',
      requestId: 'duplicate-user',
    })
    expect(state.uncertain.value).toBe(false)
    await state.submit()
    expect(writes()).toHaveLength(1)
    expect(state.ready.value).toBe(true)
  })
  it('修改本人授权后刷新Actor，失去权限则清空列表并拒绝后续写入', async () => {
    const state = useUserAdmin(
      () => current,
      async () => {
        current = actor()
      },
    )
    await state.load()
    await state.submit({
      kind: 'authorization',
      userId: current.id,
      input: emptyAuthorization(),
    })
    expect(state.ready.value).toBe(false)
    expect(state.users.value).toEqual([])
    await state.submit(create())
    expect(writes()).toHaveLength(1)
    expect(state.actionError.value).toMatchObject({ status: 403 })
  })
  it('启停只发送enabled字段，用PATCH和幂等头；确认后重新读取列表', async () => {
    const state = useUserAdmin(() => current, vi.fn())
    await state.load()
    await state.submit({ kind: 'enabled', userId: 'user-2', enabled: false })
    const [url, init] = writes()[0]
    expect(url).toBe('/api/v1/users/user-2/enabled')
    expect(init.method).toBe('PATCH')
    expect(JSON.parse(init.body)).toEqual({ enabled: false })
    expect(init.headers['Idempotency-Key']).toBeTruthy()
    expect(state.ready.value).toBe(true)
  })
})
