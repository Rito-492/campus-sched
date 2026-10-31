<script setup lang="ts">
import { computed, ref } from 'vue'
import type { CalendarEntry, ResourceView } from '../types/api'
import { formatTime } from '../domain/format'
const props = defineProps<{
  resources: ResourceView[]
  entries: CalendarEntry[]
  date: string
}>()
const selected = ref<CalendarEntry | null>(null)
const hours = Array.from({ length: 15 }, (_, i) => i + 8)
const start = computed(() => Date.parse(`${props.date}T08:00:00+08:00`))
function position(entry: CalendarEntry) {
  const left = Math.max(0, (Date.parse(entry.startsAt) - start.value) / 60000)
  const right = Math.min(840, (Date.parse(entry.endsAt) - start.value) / 60000)
  return {
    left: `${(left / 840) * 100}%`,
    width: `${(Math.max(0, right - left) / 840) * 100}%`,
  }
}
const entriesFor = (id: string) =>
  props.entries.filter((e) => e.resourceId === id)
const segmentLabel = {
  SETUP: '布场',
  EVENT: '活动',
  TEARDOWN: '撤场',
  FAULT: '故障',
}
</script>
<template>
  <p class="muted">
    {{ date }} · 北京时间 · 实色为活动，斜纹为布撤场，橙色为故障。点击查看明细。
  </p>
  <div
    class="calendar-scroll"
    tabindex="0"
    aria-label="资源日历，可横向滚动"
    data-testid="resource-calendar"
  >
    <div class="calendar-grid">
      <div class="calendar-head">
        <strong>资源 / 时刻</strong>
        <div class="hour-row">
          <span v-for="hour in hours" :key="hour">{{ hour }}:00</span>
        </div>
      </div>
      <div
        v-for="resource in resources"
        :key="resource.id"
        class="calendar-row"
      >
        <div class="resource-name">
          <strong>{{ resource.name }}</strong
          ><small>{{
            resource.kind === 'VENUE'
              ? `场地 · ${resource.capacity}人`
              : `设备 · 库存${resource.quantity}`
          }}</small>
        </div>
        <div class="calendar-lanes">
          <div
            v-for="(entry, index) in entriesFor(resource.id)"
            :key="index"
            class="calendar-lane"
          >
            <button
              class="calendar-block"
              :class="entry.segment.toLowerCase()"
              :style="position(entry)"
              :aria-label="`${resource.name} ${segmentLabel[entry.segment]} ${formatTime(entry.startsAt)}至${formatTime(entry.endsAt)} 数量${entry.quantity}`"
              @click="selected = entry"
            >
              {{ entry.label }} × {{ entry.quantity }}
            </button>
          </div>
          <p v-if="!entriesFor(resource.id).length" class="muted">
            当前查询无占用记录
          </p>
        </div>
      </div>
    </div>
  </div>
  <div class="calendar-mobile">
    <article v-for="(entry, i) in entries" :key="i" class="activity-row">
      <button class="text-button" @click="selected = entry">
        {{ resources.find((r) => r.id === entry.resourceId)?.name }} ·
        {{ segmentLabel[entry.segment] }}<br />{{
          formatTime(entry.startsAt)
        }}
        — {{ formatTime(entry.endsAt) }} · 数量 {{ entry.quantity }}
      </button>
    </article>
    <p v-if="!entries.length">当前查询无占用记录。</p>
  </div>
  <el-drawer
    :model-value="!!selected"
    title="资源占用明细"
    size="min(480px, 100vw)"
    @close="selected = null"
    ><template v-if="selected"
      ><h2>{{ selected.label }}</h2>
      <p>
        {{ formatTime(selected.startsAt) }} — {{ formatTime(selected.endsAt) }}
      </p>
      <p>
        数量：{{ selected.quantity }} · {{ segmentLabel[selected.segment] }}
      </p>
      <p v-if="selected.risk === 'AT_RISK'" class="risk-text">资源受故障影响</p>
      <RouterLink
        v-if="selected.activityId"
        :to="`/activities/${selected.activityId}`"
        @click="selected = null"
        >查看活动详情</RouterLink
      ></template
    ></el-drawer
  >
</template>
