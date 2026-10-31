<script setup lang="ts">
import { computed, ref, watch } from 'vue'
import type { CalendarEntry, ResourceView } from '../types/api'
import { formatTime } from '../domain/format'
import { capacitySlices } from '../domain/capacity'
const props = defineProps<{
  resources: ResourceView[]
  entries: CalendarEntry[]
  date: string
  truncated?: boolean
  mapLink?: (path: string) => string
}>()
const selected = ref<CalendarEntry | null>(null)
const ticks = Array.from(
  { length: 29 },
  (_, i) =>
    `${String(8 + Math.floor(i / 2)).padStart(2, '0')}:${i % 2 ? '30' : '00'}`,
)
const start = computed(() => Date.parse(`${props.date}T08:00:00+08:00`))
function position(entry: { startsAt: string; endsAt: string }) {
  const left = Math.max(0, (Date.parse(entry.startsAt) - start.value) / 60000)
  const right = Math.min(840, (Date.parse(entry.endsAt) - start.value) / 60000)
  return {
    left: `${(left / 840) * 100}%`,
    width: `${(Math.max(0, right - left) / 840) * 100}%`,
  }
}
const entriesFor = (id: string) =>
  props.entries.filter(
    (e) =>
      e.resourceId === id &&
      Date.parse(e.endsAt) > start.value &&
      Date.parse(e.startsAt) < start.value + 840 * 60000,
  )
const usage = computed(() =>
  Object.fromEntries(
    props.resources.map((r) => [
      r.id,
      capacitySlices(r, props.entries, start.value, start.value + 840 * 60000),
    ]),
  ),
)
watch(
  () => [props.entries, props.date],
  () => (selected.value = null),
)
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
          <span
            v-for="(tick, index) in ticks"
            :key="tick"
            :style="{ left: `${(index / 28) * 100}%` }"
            >{{ tick }}</span
          >
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
            v-if="resource.kind === 'EQUIPMENT'"
            class="calendar-lane"
            data-testid="equipment-usage"
          >
            <span
              v-for="(slice, i) in usage[resource.id]"
              :key="i"
              class="capacity-block"
              :class="{ warning: slice.used > slice.available }"
              :style="position(slice)"
              :title="`${formatTime(slice.startsAt)}—${formatTime(slice.endsAt)}：已用${slice.used}/总量${slice.total}，故障${slice.unavailable}，可用上限${slice.available}${truncated ? '（结果不完整）' : ''}`"
              >{{ slice.used }}/{{ slice.total
              }}<small v-if="slice.unavailable">
                故障 −{{ slice.unavailable }}</small
              ></span
            >
          </div>
          <div
            v-for="(entry, index) in entriesFor(resource.id)"
            :key="index"
            class="calendar-lane"
          >
            <button
              class="calendar-block"
              :class="entry.segment.toLowerCase()"
              :style="position(entry)"
              :title="entry.label"
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
  <details class="capacity-details" data-testid="capacity-details">
    <summary>查看各时段设备用量与故障扣减</summary>
    <p v-if="truncated" class="feedback warning">
      查询结果不完整，以下用量只是已返回记录的小计，不能据此判断空闲。
    </p>
    <article
      v-for="r in resources.filter((r) => r.kind === 'EQUIPMENT')"
      :key="r.id"
    >
      <h3>{{ r.name }}</h3>
      <p
        v-for="(s, i) in usage[r.id]"
        :key="i"
        :class="{ 'risk-text': s.used > s.available }"
      >
        {{ formatTime(s.startsAt) }} — {{ formatTime(s.endsAt) }}：已用
        {{ s.used }}/{{ s.total }} · 故障扣减 {{ s.unavailable }} · 可用上限
        {{ s.available
        }}<span v-if="s.used > s.available"> · 占用超过当前可用量</span>
      </p>
    </article>
    <p class="caption">
      按同时占用计算；故障扣减后的可用上限最低为0。查询不会预留资源，最终可行性以预约检查为准。
    </p>
  </details>
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
      <p
        v-if="!selected.activityId && selected.segment !== 'FAULT'"
        class="muted"
      >
        仅可查看占用时间与数量，活动详情及风险未向当前账号公开。
      </p>
      <RouterLink
        v-if="selected.activityId"
        :to="
          mapLink
            ? mapLink(`/activities/${selected.activityId}`)
            : `/activities/${selected.activityId}`
        "
        @click="selected = null"
        >查看活动详情</RouterLink
      ></template
    ></el-drawer
  >
</template>
