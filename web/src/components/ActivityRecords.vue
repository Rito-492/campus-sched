<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import { api } from '../api'
import type {
  Actor,
  ActivityView,
  ResourceView,
  RegistrationView,
  ChangeView,
  AuditView,
  StaffView,
} from '../types/api'
import { recordAccess, changeLabels } from '../domain/change'
import { formatTime } from '../domain/format'
import ApiFeedback from './ApiFeedback.vue'
const props = defineProps<{
  activity: ActivityView
  actor: Actor | null
  resources: ResourceView[]
}>()
const access = computed(() =>
  recordAccess(props.actor, props.activity, props.resources),
)
const tab = ref('staff'),
  loading = ref(false),
  error = ref<unknown>(null),
  truncated = ref(false)
const staff = ref<StaffView[]>([]),
  registrations = ref<RegistrationView[]>([]),
  changes = ref<ChangeView[]>([]),
  audit = ref<AuditView[]>([])
let generation = 0
async function load() {
  const turn = ++generation
  loading.value = true
  error.value = null
  truncated.value = false
  staff.value = []
  registrations.value = []
  changes.value = []
  audit.value = []
  try {
    if (tab.value === 'staff' && props.activity.input.staffUserIds.length) {
      const r = await api.staff()
      if (turn !== generation) return
      staff.value = r.data
      truncated.value = !!r.truncated
    } else if (tab.value === 'registrations' && access.value.registrations) {
      const r = await api.registrations(props.activity.id)
      if (turn !== generation) return
      registrations.value = r.data
      truncated.value = !!r.truncated
    } else if (tab.value === 'changes' && access.value.changes) {
      const r = await api.changes(props.activity.id)
      if (turn !== generation) return
      changes.value = r.data
      truncated.value = !!r.truncated
    } else if (tab.value === 'audit' && access.value.audit) {
      const r = await api.audit(props.activity.id)
      if (turn !== generation) return
      audit.value = r.data
      truncated.value = !!r.truncated
    }
  } catch (e) {
    if (turn === generation) error.value = e
  } finally {
    if (turn === generation) loading.value = false
  }
}
watch(
  [tab, () => props.activity, () => props.actor],
  () => {
    if (
      tab.value !== 'staff' &&
      !access.value[tab.value as keyof typeof access.value]
    )
      tab.value = 'staff'
    void load()
  },
  { immediate: true },
)
</script>
<template>
  <section class="panel" data-testid="activity-records">
    <h2>人员与记录</h2>
    <el-tabs v-model="tab"
      ><el-tab-pane name="staff" label="工作人员" /><el-tab-pane
        v-if="access.registrations"
        name="registrations"
        label="报名名单" /><el-tab-pane
        v-if="access.changes"
        name="changes"
        label="改期记录" /><el-tab-pane
        v-if="access.audit"
        name="audit"
        label="操作历史"
    /></el-tabs>
    <ApiFeedback
      :loading="loading"
      :error="error"
      :truncated="truncated"
      @retry="load"
    />
    <template v-if="!loading && !error">
      <template v-if="tab === 'staff'"
        ><p v-if="!activity.input.staffUserIds.length" class="muted">
          当前权限下没有可展示的工作人员。
        </p>
        <p v-for="id in activity.input.staffUserIds" :key="id">
          {{ staff.find((s) => s.id === id)?.displayName ?? id }}
        </p></template
      >
      <div v-if="tab === 'registrations'" data-testid="registration-list">
        <p>
          服务端当前报名人数：{{
            activity.registrationCount
          }}；名单仅展示当前权限允许的记录。
        </p>
        <p v-if="!registrations.length" class="muted">暂无可展示的报名记录。</p>
        <div v-for="r in registrations" :key="r.userId" class="notification">
          {{ r.userId }} ·
          {{
            { ACTIVE: '有效报名', WITHDRAWN: '已退出', CANCELLED: '已取消' }[
              r.status
            ]
          }}
          · 版本 {{ r.versionNo }}
        </div>
      </div>
      <template v-if="tab === 'changes'"
        ><p v-if="!changes.length" class="muted">暂无改期记录。</p>
        <article v-for="c in changes" :key="c.id" class="notification">
          <RouterLink :to="`/changes/${c.id}`"
            >{{ changeLabels[c.status] }} · 基于版本
            {{ c.baseVersionNo }}</RouterLink
          >
          <p>{{ c.reason }}</p>
          <p class="caption">{{ formatTime(c.createdAt) }}</p>
        </article></template
      >
      <template v-if="tab === 'audit'"
        ><p v-if="!audit.length" class="muted">暂无可展示的历史记录。</p>
        <article v-for="a in audit" :key="a.id" class="notification">
          <h3>{{ a.summary }}</h3>
          <p>
            {{ formatTime(a.createdAt) }} · 操作者 {{ a.actorId }} · 版本
            {{ a.versionNo }}
          </p>
        </article></template
      >
    </template>
  </section>
</template>
