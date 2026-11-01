import { computed, ref, shallowRef } from 'vue'
import { ApiError, type Envelope } from '../api/client'

export interface Mutation<T> {
  run: () => Promise<Envelope<T>>
}
// 未知结果始终保留同一个 command；成功后的查询失败不能重做已确认的写入。
export function useMutation<T = unknown>(
  options: {
    confirmed?: (data: T) => void | Promise<void>
    conflict?: () => void | Promise<void>
  } = {},
) {
  const busy = ref(false),
    uncertain = ref(false),
    error = shallowRef<unknown>(null)
  const refreshError = shallowRef<unknown>(null),
    message = ref('')
  let pending: Mutation<T> | null = null
  async function execute(next?: Mutation<T>) {
    if (busy.value || (next && uncertain.value)) return
    if (next) pending = next
    if (!pending) return
    busy.value = true
    error.value = null
    refreshError.value = null
    message.value = ''
    try {
      let data: T
      try {
        data = (await pending.run()).data
      } catch (e) {
        error.value = e
        uncertain.value =
          !(e instanceof ApiError) || e.status === 0 || e.status >= 500
        if (!uncertain.value) pending = null
        if (e instanceof ApiError && e.status === 409) {
          try {
            await options.conflict?.()
          } catch (refresh) {
            refreshError.value = refresh
          }
        }
        return
      }
      pending = null
      uncertain.value = false
      message.value = '操作已由服务端确认。'
      try {
        await options.confirmed?.(data)
      } catch (e) {
        refreshError.value = e
      }
    } finally {
      busy.value = false
    }
  }
  return {
    busy,
    uncertain,
    error,
    refreshError,
    message,
    locked: computed(() => busy.value || uncertain.value),
    execute,
  }
}
