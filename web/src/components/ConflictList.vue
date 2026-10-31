<script setup lang="ts">
import type { Conflict } from '../types/api'
import { formatTime } from '../domain/format'
defineProps<{ conflicts: Conflict[] }>()
</script>
<template>
  <div data-testid="conflict-list">
    <article v-for="(conflict, i) in conflicts" :key="i" class="feedback error">
      <strong>{{ conflict.resourceName }}</strong>
      <p>{{ conflict.message }}</p>
      <p>
        {{ formatTime(conflict.startsAt) }} — {{ formatTime(conflict.endsAt) }}
      </p>
      <p v-if="conflict.required !== null && conflict.available !== null">
        需要 {{ conflict.required }} / 可用 {{ conflict.available }}
      </p>
    </article>
    <p v-if="!conflicts.length" class="muted">
      当前检查未返回冲突；预览不代表已预约。
    </p>
  </div>
</template>
