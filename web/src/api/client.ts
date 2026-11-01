export interface Envelope<T> {
  data: T
  requestId: string
  truncated?: boolean
}
export class ApiError extends Error {
  constructor(
    public status: number,
    public code: string,
    message: string,
    public requestId = '',
    public details: unknown = null,
  ) {
    super(message)
  }
}
export const tokenKey = 'campus-sched.session'
export const readToken = () => sessionStorage.getItem(tokenKey)
export function clearSession() {
  sessionStorage.removeItem(tokenKey)
  window.dispatchEvent(new Event('session-expired'))
}

export async function request<T>(
  path: string,
  options: {
    method?: string
    body?: unknown
    key?: string
    anonymous?: boolean
    timeoutMs?: number
  } = {},
): Promise<Envelope<T>> {
  const controller = new AbortController()
  const timer = setTimeout(() => controller.abort(), options.timeoutMs ?? 15000)
  const headers: Record<string, string> = { Accept: 'application/json' }
  const token = readToken()
  if (token && !options.anonymous) headers.Authorization = `Bearer ${token}`
  if (options.body !== undefined) headers['Content-Type'] = 'application/json'
  if (options.key) headers['Idempotency-Key'] = options.key
  try {
    const response = await fetch(`/api/v1${path}`, {
      method: options.method ?? 'GET',
      headers,
      body:
        options.body === undefined ? undefined : JSON.stringify(options.body),
      signal: controller.signal,
      cache: 'no-store',
    })
    if (response.status === 401 && !options.anonymous) clearSession()
    const payload = await response.json().catch(() => null)
    if (!response.ok)
      throw new ApiError(
        response.status,
        payload?.error?.code ?? 'HTTP_ERROR',
        payload?.error?.message ?? '服务暂不可用，请稍后重试。',
        payload?.requestId,
        payload?.error?.details,
      )
    if (
      !payload ||
      !('data' in payload) ||
      typeof payload.requestId !== 'string'
    )
      throw new ApiError(
        502,
        'INVALID_RESPONSE',
        '服务响应格式不符合约定，请联系维护人员。',
      )
    return payload as Envelope<T>
  } catch (error) {
    if (error instanceof ApiError) throw error
    throw new ApiError(
      0,
      'NETWORK_ERROR',
      '网络中断或超时，操作结果尚未确认。请先查询状态，再使用原请求重试。',
    )
  } finally {
    clearTimeout(timer)
  }
}

// 每个命令冻结请求体和 key。失败重试调用同一 run，不重新生成 key。
export function command<T>(path: string, body: unknown, method = 'POST') {
  const snapshot =
    body === undefined
      ? undefined
      : (JSON.parse(JSON.stringify(body)) as unknown)
  const key = crypto.randomUUID()
  return { key, run: () => request<T>(path, { method, body: snapshot, key }) }
}
