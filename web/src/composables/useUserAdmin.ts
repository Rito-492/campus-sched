import { computed, ref, shallowRef } from 'vue'
import { api } from '../api'
import { ApiError, type Envelope } from '../api/client'
import {
  hasAccountAccess,
  type OrganizationOption,
  type UserOperation,
} from '../domain/authorization'
import type { Actor, ResourceView } from '../types/api'

export function useUserAdmin(
  actor: () => Actor | null,
  refreshActor: () => Promise<void>,
) {
  const users = ref<Actor[]>([]),
    organizations = ref<OrganizationOption[]>([]),
    resources = ref<ResourceView[]>([])
  const loading = ref(false),
    busy = ref(false),
    ready = ref(false),
    truncated = ref(false)
  const loadError = shallowRef<unknown>(null),
    actionError = shallowRef<unknown>(null)
  const message = ref(''),
    completed = ref(0),
    uncertain = ref(false)
  let pending: { run: () => Promise<Envelope<unknown>> } | null = null
  let needsActorRefresh = false
  const blocked = computed(() => busy.value || uncertain.value)

  async function load() {
    if (loading.value) return
    loading.value = true
    ready.value = false
    loadError.value = null
    try {
      if (needsActorRefresh) {
        await refreshActor()
        needsActorRefresh = false
      }
      if (!hasAccountAccess(actor()))
        throw new ApiError(403, 'FORBIDDEN', '当前账号没有账号管理权限')
      const [u, o, r] = await Promise.all([
        api.users(),
        api.organizations(),
        api.resources(),
      ])
      users.value = u.data
      organizations.value = o.data
      resources.value = r.data
      truncated.value = !!u.truncated || !!o.truncated || !!r.truncated
      ready.value = true
    } catch (e) {
      users.value = []
      loadError.value = e
    } finally {
      loading.value = false
    }
  }
  async function submit(operation?: UserOperation) {
    if (busy.value || (uncertain.value && operation)) return
    if (!hasAccountAccess(actor())) {
      actionError.value = new ApiError(
        403,
        'FORBIDDEN',
        '当前账号没有账号管理权限',
      )
      return
    }
    if (operation) {
      if (!ready.value) return
      pending =
        operation.kind === 'create'
          ? api.createUser(operation.input)
          : operation.kind === 'authorization'
            ? api.authorization(operation.userId, operation.input)
            : api.enableUser(operation.userId, operation.enabled)
    }
    if (!pending) return
    busy.value = true
    actionError.value = null
    message.value = ''
    try {
      await pending.run()
      // 写入确认后先丢弃命令。后续刷新失败只能重读，不能重放成功的创建。
      pending = null
      uncertain.value = false
      completed.value++
      message.value = '账号变更已由服务端确认。'
    } catch (e) {
      actionError.value = e
      uncertain.value =
        !(e instanceof ApiError) || e.status === 0 || e.status >= 500
      if (!uncertain.value) pending = null
      if (e instanceof ApiError && e.status === 409) await load()
      busy.value = false
      return
    }
    try {
      ready.value = false
      await refreshActor()
      if (hasAccountAccess(actor())) await load()
      else {
        users.value = []
        organizations.value = []
        resources.value = []
      }
    } catch (e) {
      loadError.value = e
      ready.value = false
      needsActorRefresh = true
    } finally {
      busy.value = false
    }
  }
  return {
    users,
    organizations,
    resources,
    loading,
    busy,
    ready,
    truncated,
    loadError,
    actionError,
    message,
    completed,
    uncertain,
    blocked,
    load,
    submit,
  }
}
