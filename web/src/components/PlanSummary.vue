<script setup lang="ts">
import type { Plan, ResourceView } from '../types/api'
import { formatTime } from '../domain/format'
withDefaults(
  defineProps<{
    plan: Plan
    resources?: ResourceView[]
    changedFields?: string[]
  }>(),
  { resources: () => [], changedFields: () => [] },
)
</script>
<template>
  <dl class="summary plan-summary">
    <dt>时间</dt>
    <dd
      :class="{
        'changed-field': changedFields.some((f) =>
          ['startAt', 'endAt'].includes(f.replace('plan.', '')),
        ),
      }"
    >
      {{ formatTime(plan.startAt) }} — {{ formatTime(plan.endAt) }}
    </dd>
    <dt>布场 / 撤场</dt>
    <dd
      :class="{
        'changed-field': changedFields.some((f) =>
          ['setupMinutes', 'teardownMinutes'].includes(f.replace('plan.', '')),
        ),
      }"
    >
      {{ plan.setupMinutes }} / {{ plan.teardownMinutes }} 分钟
    </dd>
    <dt>场地</dt>
    <dd
      :class="{
        'changed-field': changedFields.some((f) => f.endsWith('venueId')),
      }"
    >
      {{ resources.find((r) => r.id === plan.venueId)?.name ?? plan.venueId }}
    </dd>
    <dt>设备</dt>
    <dd
      :class="{
        'changed-field': changedFields.some((f) => f.includes('equipment')),
      }"
    >
      <p v-for="n in plan.equipment" :key="n.resourceId">
        {{ resources.find((r) => r.id === n.resourceId)?.name ?? n.resourceId }}
        × {{ n.quantity }}
      </p>
      <span v-if="!plan.equipment.length">无设备需求</span>
    </dd>
  </dl>
</template>
