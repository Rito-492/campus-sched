<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { api } from '../api'
import type { CalendarEntry, ResourceView } from '../types/api'
import ApiFeedback from '../components/ApiFeedback.vue'
import ResourceCalendar from '../components/ResourceCalendar.vue'
const date = ref(
  new Intl.DateTimeFormat('en-CA', {
    timeZone: 'Asia/Shanghai',
    year: 'numeric',
    month: '2-digit',
    day: '2-digit',
  }).format(new Date()),
)
const resourceId = ref(''),
  resources = ref<ResourceView[]>([]),
  entries = ref<CalendarEntry[]>([]),
  error = ref<unknown>(null),
  loading = ref(false),
  truncated = ref(false)
let generation = 0
async function load() {
  const turn = ++generation
  loading.value = true
  error.value = null
  entries.value = []
  if (!date.value) {
    error.value = new Error('请选择查询日期')
    loading.value = false
    return
  }
  try {
    const from = `${date.value}T00:00:00+08:00`,
      to = new Date(Date.parse(from) + 86400000).toISOString()
    const [r, c] = await Promise.all([
      api.resources(),
      api.calendar(from, to, resourceId.value),
    ])
    if (turn !== generation) return
    resources.value = r.data
    entries.value = c.data
    truncated.value = !!r.truncated || !!c.truncated
  } catch (e) {
    if (turn === generation) error.value = e
  } finally {
    if (turn === generation) loading.value = false
  }
}
watch([date, resourceId], load, { immediate: true })
onBeforeUnmount(() => generation++)
</script>
<template>
  <section class="page-heading">
    <span class="eyebrow">协同工作 / 资源</span>
    <h1>资源日历</h1>
    <p class="muted">先查看安排，再规划活动。查询不会产生预约。</p>
  </section>
  <section class="panel">
    <div class="toolbar" data-testid="calendar-nav">
      <label for="calendar-date">日期</label
      ><input id="calendar-date" v-model="date" type="date" /><el-select
        v-model="resourceId"
        clearable
        placeholder="全部资源"
        aria-label="筛选资源"
        class="filter-select"
        ><el-option
          v-for="r in resources"
          :key="r.id"
          :value="r.id"
          :label="r.name" /></el-select
      ><el-button :disabled="loading" @click="load">刷新</el-button>
    </div>
    <ApiFeedback
      :error="error"
      :loading="loading"
      :truncated="truncated"
      @retry="load"
    /><ResourceCalendar
      v-if="!loading && !error"
      :date="date"
      :resources="
        resourceId ? resources.filter((r) => r.id === resourceId) : resources
      "
      :entries="entries"
      :truncated="truncated"
    />
    <p class="caption">
      设备用量按同时发生的占用计算；数据可能在查看后变化，最终预约仍需服务端检查。
    </p>
  </section>
</template>
