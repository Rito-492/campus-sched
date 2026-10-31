<script setup lang="ts">
import { computed, onBeforeUnmount, ref, watch } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import type { ActivityView, ChangeView } from '../types/api'
import { mapSettled } from '../domain/queue'
import { pendingChange, changeLabels } from '../domain/change'
import { useSession } from '../stores/session'
import { isApplicant } from '../domain/access'
import { statusLabels } from '../domain/format'
import ActivityList from '../components/ActivityList.vue'
import ApiFeedback from '../components/ApiFeedback.vue'
const reviews = computed(() => useRouteValue.path === '/reviews')
const useRouteValue = useRoute(),
  session = useSession()
const scope = ref<'mine' | 'public'>(
    isApplicant(session.actor) ? 'mine' : 'public',
  ),
  status = ref(''),
  items = ref<ActivityView[]>([]),
  loading = ref(false),
  error = ref<unknown>(null),
  truncated = ref(false),
  changes = ref<ChangeView[]>([]),
  partial = ref<string[]>([])
let generation = 0
async function load() {
  const turn = ++generation
  loading.value = true
  error.value = null
  items.value = []
  changes.value = []
  partial.value = []
  truncated.value = false
  try {
    const result = await api.activities(
      reviews.value ? 'review' : scope.value,
      status.value,
    )
    if (turn !== generation) return
    items.value = result.data
    truncated.value = !!result.truncated
    if (reviews.value) {
      const targets = result.data.slice(0, 200).filter((a) => a.activeChangeId)
      const related = await mapSettled(
        targets,
        (a) => api.changes(a.id),
        () => turn === generation,
      )
      if (turn !== generation) return
      related.forEach((r, index) => {
        if (r.status === 'fulfilled') {
          changes.value.push(
            ...r.value.data.filter((c) => pendingChange(c.status)),
          )
          truncated.value ||= !!r.value.truncated
        } else
          partial.value.push(
            `${targets[index].input.title}：${r.reason instanceof Error ? r.reason.message : '改期记录加载失败'}`,
          )
      })
    }
  } catch (e) {
    if (turn === generation) error.value = e
  } finally {
    if (turn === generation) loading.value = false
  }
}
watch([scope, status, reviews], load, { immediate: true })
onBeforeUnmount(() => generation++)
</script>
<template>
  <section class="page-heading row">
    <div>
      <span class="eyebrow">协同工作 / {{ reviews ? '待办' : '活动' }}</span>
      <h1>{{ reviews ? '审批与待办' : '活动工作台' }}</h1>
      <p class="muted">
        {{
          reviews
            ? '按授权范围处理申请，所有者不能审批自己的活动。'
            : '查看活动安排、参与进度与资源状态。'
        }}
      </p>
    </div>
    <RouterLink
      v-if="!reviews && isApplicant(session.actor)"
      class="primary-link"
      to="/activities/new"
      data-testid="activity-create"
      >＋ 创建活动</RouterLink
    >
  </section>
  <section class="panel">
    <div class="toolbar">
      <el-radio-group v-if="!reviews" v-model="scope" aria-label="活动范围"
        ><el-radio-button v-if="isApplicant(session.actor)" value="mine"
          >我的活动</el-radio-button
        ><el-radio-button value="public"
          >公开活动</el-radio-button
        ></el-radio-group
      ><el-select
        v-model="status"
        placeholder="全部状态"
        clearable
        aria-label="活动状态"
        class="filter-select"
        ><el-option
          v-for="(label, value) in statusLabels"
          :key="value"
          :label="label"
          :value="value" /></el-select
      ><el-button :disabled="loading" @click="load">刷新</el-button>
    </div>
    <ApiFeedback
      :error="error"
      :loading="loading"
      :truncated="truncated"
      @retry="load"
    />
    <div v-if="!loading && !error && !items.length" class="empty-state">
      <h2>{{ reviews ? '暂无待处理事项' : '暂无活动' }}</h2>
      <p>调整筛选条件，或稍后刷新。</p>
    </div>
    <ActivityList :items="items" />
    <template v-if="reviews">
      <h2>待处理改期</h2>
      <p class="caption">
        包含已开放报名活动的改期。进入后按当前职责确认；已读通知不会推进此流程。
      </p>
      <div v-if="partial.length" class="feedback warning">
        <p>部分改期未能加载，以下活动的待办尚未核对：</p>
        <ul>
          <li v-for="text in partial" :key="text">{{ text }}</li>
        </ul>
        <el-button :disabled="loading" @click="load">重新查询</el-button>
      </div>
      <article
        v-for="c in changes"
        :key="c.id"
        class="notification"
        data-testid="review-change"
      >
        <RouterLink :to="`/changes/${c.id}`"
          >{{ items.find((a) => a.id === c.activityId)?.input.title }} ·
          {{ changeLabels[c.status] }}</RouterLink
        >
        <p>{{ c.reason }}</p>
      </article>
      <p
        v-if="!loading && !error && !partial.length && !changes.length"
        class="muted"
      >
        当前已返回活动中暂无待处理改期。
      </p>
    </template>
  </section>
</template>
