<script setup lang="ts">
import { onBeforeUnmount, onMounted, ref } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import { api } from '../api'
import { useSession } from '../stores/session'
import { hasPermission } from '../domain/access'
import { mayManageResource } from '../domain/resource'
import { useMutation } from '../composables/useMutation'
import type {
  ResourceView,
  FaultView,
  ResourceCreate,
  ResourceEligibility,
  FaultInput,
} from '../types/api'
import ResourceManagePanel from '../components/ResourceManagePanel.vue'
import ApiFeedback from '../components/ApiFeedback.vue'
import MutationFeedback from '../components/MutationFeedback.vue'
const session = useSession(),
  router = useRouter()
const resources = ref<ResourceView[]>([]),
  faults = ref<FaultView[]>([]),
  loading = ref(false),
  faultLoading = ref(false),
  error = ref<unknown>(null),
  faultError = ref<unknown>(null),
  truncated = ref(false),
  faultTruncated = ref(false),
  revision = ref(0),
  actorFresh = ref(false)
let query: [string, string, string] | null = null,
  generation = 0
const mutation = useMutation({
  confirmed: async () => {
    await load(true)
    revision.value++
    if (query) await loadFaults(...query)
  },
  conflict: () => load(true),
})
async function load(rethrow = false) {
  loading.value = true
  error.value = null
  actorFresh.value = false
  try {
    await session.restore()
    if (!hasPermission(session.actor, 'RESOURCE_MANAGE')) {
      resources.value = []
      faults.value = []
      await router.replace('/forbidden')
      return
    }
    actorFresh.value = true
    const r = await api.resources()
    resources.value = r.data
    truncated.value = !!r.truncated
  } catch (e) {
    error.value = e
    if (rethrow) throw e
  } finally {
    loading.value = false
  }
}
async function loadFaults(id: string, from: string, to: string) {
  query = [id, from, to]
  const turn = ++generation
  faults.value = []
  faultError.value = null
  faultTruncated.value = false
  if (!mayManageResource(session.actor, id)) return
  faultLoading.value = true
  try {
    const r = await api.faults(id, from, to)
    if (turn === generation) {
      faults.value = r.data
      faultTruncated.value = !!r.truncated
    }
  } catch (e) {
    if (turn === generation) faultError.value = e
  } finally {
    if (turn === generation) faultLoading.value = false
  }
}
function create(data: ResourceCreate) {
  if (actorFresh.value && !loading.value)
    void mutation.execute(api.createResource(data))
}
function eligibility(id: string, data: ResourceEligibility) {
  if (
    actorFresh.value &&
    !loading.value &&
    mayManageResource(session.actor, id)
  )
    void mutation.execute(api.eligibility(id, data))
}
function fault(id: string, data: FaultInput) {
  if (
    actorFresh.value &&
    !loading.value &&
    mayManageResource(session.actor, id)
  )
    void mutation.execute(api.createFault(id, data))
}
onMounted(() => load())
onBeforeUnmount(() => generation++)
onBeforeRouteLeave(
  (to) =>
    to.path === '/login' ||
    !mutation.uncertain.value ||
    window.confirm('资源操作结果尚未确认。离开后请先查询状态，确定离开？'),
)
</script>
<template>
  <section class="page-heading">
    <span class="eyebrow">管理 / 资源</span>
    <h1>资源管理</h1>
    <p class="muted">维护资源、申请资格与故障记录，让活动安排有据可查。</p>
  </section>
  <div class="toolbar">
    <el-button :disabled="loading || mutation.busy.value" @click="load()"
      >刷新资源与权限</el-button
    ><RouterLink to="/resources/calendar">查看资源日历</RouterLink>
  </div>
  <ApiFeedback
    :error="error"
    :loading="loading"
    :truncated="truncated"
    @retry="load()"
  />
  <MutationFeedback :mutation="mutation" @refresh="load()" />
  <ApiFeedback
    :error="faultError"
    :loading="faultLoading"
    :truncated="faultTruncated"
    @retry="query && loadFaults(...query)"
  />
  <ResourceManagePanel
    :actor="session.actor"
    :resources="resources"
    :faults="faults"
    :disabled="loading || mutation.locked.value || !actorFresh || !!error"
    :revision="revision"
    @create="create"
    @eligibility="eligibility"
    @fault="fault"
    @query="loadFaults"
  />
</template>
