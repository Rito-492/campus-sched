<script setup lang="ts">
import { ApiError } from '../api/client'
defineProps<{ error?: unknown; loading?: boolean; truncated?: boolean }>()
defineEmits<{ retry: [] }>()
</script>
<template>
  <div v-if="error" class="feedback error" role="alert" data-testid="api-error">
    <strong>{{
      error instanceof Error ? error.message : '加载失败，请重试'
    }}</strong>
    <p v-if="error instanceof ApiError && error.requestId">
      追踪编号：{{ error.requestId }}
    </p>
    <details v-if="error instanceof ApiError && error.details">
      <summary>查看服务端详细原因</summary>
      <pre class="error-details">{{
        JSON.stringify(error.details, null, 2)
      }}</pre>
    </details>
    <el-button size="small" @click="$emit('retry')">重试</el-button>
  </div>
  <el-skeleton v-if="loading" :rows="3" animated aria-label="正在加载" />
  <p v-if="truncated" class="feedback warning" role="status">
    结果已截断，请缩小查询范围；未显示的记录不代表不存在或空闲。
  </p>
</template>
