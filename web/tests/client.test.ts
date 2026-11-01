import { afterEach, beforeEach, describe, expect, it, vi } from 'vitest'
import { ApiError, command, request, tokenKey } from '../src/api/client'
const response = (data: unknown, status = 200) =>
  new Response(JSON.stringify(data), {
    status,
    headers: { 'Content-Type': 'application/json' },
  })
describe('统一 API 客户端', () => {
  const fetcher = vi.fn()
  beforeEach(() => {
    sessionStorage.clear()
    vi.stubGlobal('fetch', fetcher)
    fetcher.mockReset()
  })
  afterEach(() => vi.unstubAllGlobals())
  it('使用统一前缀与Bearer并保留截断标志及requestId', async () => {
    sessionStorage.setItem(tokenKey, 'test-token')
    fetcher.mockResolvedValue(
      response({ data: [], truncated: true, requestId: 'r1' }),
    )
    expect(await request('/activities')).toEqual({
      data: [],
      truncated: true,
      requestId: 'r1',
    })
    expect(fetcher).toHaveBeenCalledWith(
      '/api/v1/activities',
      expect.objectContaining({
        headers: expect.objectContaining({
          Authorization: 'Bearer test-token',
        }),
      }),
    )
  })
  it('401清除会话，403保留会话及服务端原因和详情', async () => {
    sessionStorage.setItem(tokenKey, 'test-token')
    fetcher.mockResolvedValueOnce(
      response(
        {
          error: {
            code: 'RESOURCE_INELIGIBLE',
            message: '仅教师可申请',
            details: { resourceId: 'r' },
          },
          requestId: 'r2',
        },
        403,
      ),
    )
    await expect(request('/resources')).rejects.toMatchObject({
      status: 403,
      message: '仅教师可申请',
      requestId: 'r2',
      details: { resourceId: 'r' },
    })
    expect(sessionStorage.getItem(tokenKey)).toBe('test-token')
    fetcher.mockResolvedValue(
      response(
        {
          error: { code: 'UNAUTHENTICATED', message: '会话失效' },
          requestId: 'r3',
        },
        401,
      ),
    )
    await expect(request('/auth/me')).rejects.toBeInstanceOf(ApiError)
    expect(sessionStorage.getItem(tokenKey)).toBeNull()
  })
  it('登录不带现有Bearer；登录失败不触发受保护会话处理', async () => {
    sessionStorage.setItem(tokenKey, 'old')
    fetcher.mockResolvedValue(
      response(
        {
          error: { code: 'UNAUTHENTICATED', message: '登录失败' },
          requestId: 'r',
        },
        401,
      ),
    )
    await expect(
      request('/auth/login', { method: 'POST', anonymous: true }),
    ).rejects.toBeInstanceOf(ApiError)
    expect(fetcher.mock.calls[0][1].headers.Authorization).toBeUndefined()
  })
  it('网络失败后重用相同key和原请求体，不能受用户后续编辑影响', async () => {
    const body = { expectedLockVersion: 3 }
    const c = command('/activities/a/submit', body)
    body.expectedLockVersion = 4
    fetcher
      .mockRejectedValueOnce(new TypeError('network'))
      .mockResolvedValueOnce(response({ data: { ok: true }, requestId: 'r' }))
    await expect(c.run()).rejects.toMatchObject({
      status: 0,
      code: 'NETWORK_ERROR',
    })
    await c.run()
    const [first, second] = fetcher.mock.calls
    expect(first[1].headers['Idempotency-Key']).toBe(
      second[1].headers['Idempotency-Key'],
    )
    expect(first[1].body).toBe('{"expectedLockVersion":3}')
    expect(second[1].body).toBe(first[1].body)
  })
  it('409保留真实业务码和最新版本，不包装为成功', async () => {
    fetcher.mockResolvedValue(
      response(
        {
          error: {
            code: 'STALE_VERSION',
            message: '旧版本',
            details: { lockVersion: 8 },
          },
          requestId: 'r',
        },
        409,
      ),
    )
    await expect(
      command('/activities/a/submit', { expectedLockVersion: 7 }).run(),
    ).rejects.toMatchObject({
      code: 'STALE_VERSION',
      details: { lockVersion: 8 },
    })
  })
  it('拒绝成功HTTP中的非法信封和HTML错误页', async () => {
    fetcher
      .mockResolvedValueOnce(response({ ok: true }))
      .mockResolvedValueOnce(
        new Response('<html>bad gateway</html>', { status: 502 }),
      )
    await expect(request('/auth/me')).rejects.toMatchObject({
      code: 'INVALID_RESPONSE',
    })
    await expect(request('/auth/me')).rejects.toMatchObject({ status: 502 })
  })
  it('超时中止请求并报告结果未知，不自动重发', async () => {
    vi.useFakeTimers()
    fetcher.mockImplementation(
      (_url, init: RequestInit) =>
        new Promise((_resolve, reject) =>
          init.signal?.addEventListener('abort', () =>
            reject(new DOMException('aborted', 'AbortError')),
          ),
        ),
    )
    const result = expect(
      request('/auth/me', { timeoutMs: 50 }),
    ).rejects.toMatchObject({ code: 'NETWORK_ERROR' })
    await vi.advanceTimersByTimeAsync(51)
    await result
    expect(fetcher).toHaveBeenCalledTimes(1)
    vi.useRealTimers()
  })
})
