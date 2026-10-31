<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import type { ActivityView } from '../types/api'
import { useSession } from '../stores/session'
import { isApplicant } from '../domain/access'
import { formatTime, statusLabels } from '../domain/format'
import ApiFeedback from '../components/ApiFeedback.vue'
const reviews = computed(() => useRouteValue.path === '/reviews')
const useRouteValue = useRoute(),
  session = useSession()
const scope = ref<'mine' | 'public'>('mine'),
  status = ref(''),
  items = ref<ActivityView[]>([]),
  loading = ref(false),
  error = ref<unknown>(null),
  truncated = ref(false)
async function load() {
  loading.value = true
  error.value = null
  items.value = []
  try {
    const result = await api.activities(
      reviews.value ? 'review' : scope.value,
      status.value,
    )
    items.value = result.data
    truncated.value = !!result.truncated
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}
onMounted(load)
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
      <el-radio-group
        v-if="!reviews"
        v-model="scope"
        aria-label="活动范围"
        @change="load"
        ><el-radio-button value="mine">我的活动</el-radio-button
        ><el-radio-button value="public"
          >公开活动</el-radio-button
        ></el-radio-group
      ><el-select
        v-model="status"
        placeholder="全部状态"
        clearable
        aria-label="活动状态"
        class="filter-select"
        @change="load"
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
    <div class="activity-list" data-testid="activity-list">
      <article v-for="item in items" :key="item.id" class="activity-row">
        <div class="date-tile">
          {{ formatTime(item.input.plan.startAt).split(' ')[0] }}
        </div>
        <div class="grow">
          <span class="badge">{{ statusLabels[item.status] }}</span>
          <h2>
            <RouterLink :to="`/activities/${item.id}`">{{
              item.input.title
            }}</RouterLink>
          </h2>
          <p class="muted">
            {{ formatTime(item.input.plan.startAt) }} —
            {{ formatTime(item.input.plan.endAt) }} ·
            {{ item.registrationCount }}/{{
              item.input.registrationCapacity
            }}
            人报名
          </p>
          <p v-if="item.resourceRisk === 'AT_RISK'" class="risk-text">
            ⚠ 资源受故障影响
          </p>
          <RouterLink
            v-if="item.activeChangeId"
            :to="`/changes/${item.activeChangeId}`"
            >查看待处理改期</RouterLink
          >
        </div>
        <RouterLink :to="`/activities/${item.id}`">查看详情 →</RouterLink>
      </article>
    </div>
  </section>
</template>
