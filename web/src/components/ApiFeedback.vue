<script setup lang="ts">
import { computed } from 'vue'
import { ApiError } from '../api/client'
import type { ResourceView } from '../types/api'
const props = defineProps<{
  error?: unknown
  loading?: boolean
  truncated?: boolean
  testId?: string
  resources?: Pick<ResourceView, 'id' | 'name'>[]
}>()
defineEmits<{ retry: [] }>()
const restricted = computed(() => {
  const error = props.error
  if (!(error instanceof ApiError) || error.code !== 'RESOURCE_INELIGIBLE')
    return null
  const details = error.details
  const ids =
    details &&
    typeof details === 'object' &&
    'resourceIds' in details &&
    Array.isArray(details.resourceIds)
      ? details.resourceIds.filter((id): id is string => typeof id === 'string')
      : []
  return ids.map((id) => props.resources?.find((r) => r.id === id)?.name ?? id)
})
</script>
<template>
  <div v-if="error" class="feedback error" role="alert" data-testid="api-error">
    <div :data-testid="testId ?? 'alert-generic'">
      <strong>{{
        error instanceof Error ? error.message : '加载失败，请重试'
      }}</strong>
      <p v-if="restricted" data-testid="eligibility-error">
        <template v-if="restricted.length"
          >资格受限资源：{{ restricted.join('、') }}。</template
        >
        <template v-else>服务端未提供受限资源清单。</template>
        请调整资源选择，或联系管理员核对申请人身份与资源资格。
      </p>
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
  </div>
  <el-skeleton v-if="loading" :rows="3" animated aria-label="正在加载" />
  <p v-if="truncated" class="feedback warning" role="status">
    结果已截断，请缩小查询范围；未显示的记录不代表不存在或空闲。
  </p>
</template>
