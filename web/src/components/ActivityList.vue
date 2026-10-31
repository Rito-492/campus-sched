<script setup lang="ts">
import type { ActivityView } from '../types/api'
import { formatTime, statusLabels } from '../domain/format'
const props = defineProps<{
  items: ActivityView[]
  mapLink?: (path: string) => string
}>()
const link = (path: string) => (props.mapLink ? props.mapLink(path) : path)
</script>
<template>
  <div class="activity-list" data-testid="activity-list">
    <article v-for="item in items" :key="item.id" class="activity-row">
      <div class="date-tile">
        {{ formatTime(item.input.plan.startAt).split(' ')[0] }}
      </div>
      <div class="grow">
        <span class="badge">{{ statusLabels[item.status] }}</span>
        <h2>
          <RouterLink :to="link(`/activities/${item.id}`)">{{
            item.input.title
          }}</RouterLink>
        </h2>
        <p class="muted">
          {{ formatTime(item.input.plan.startAt) }} —
          {{ formatTime(item.input.plan.endAt) }} ·
          {{ item.registrationCount }}/{{ item.input.registrationCapacity }}
          人报名
        </p>
        <p v-if="item.resourceRisk === 'AT_RISK'" class="risk-text">
          ⚠ 资源受故障影响
        </p>
        <RouterLink
          v-if="item.activeChangeId"
          :to="link(`/changes/${item.activeChangeId}`)"
          >查看待处理改期</RouterLink
        >
      </div>
      <RouterLink :to="link(`/activities/${item.id}`)">查看详情 →</RouterLink>
    </article>
  </div>
</template>
