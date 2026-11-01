<script setup lang="ts">
import type { useMutation } from '../composables/useMutation'
import type { ResourceView } from '../types/api'
import ApiFeedback from './ApiFeedback.vue'
defineProps<{
  resources?: ResourceView[]
  mutation: Omit<ReturnType<typeof useMutation>, 'execute'> & {
    execute: () => Promise<void>
  }
}>()
defineEmits<{ refresh: [] }>()
</script>
<template>
  <p v-if="mutation.message.value" class="feedback success" role="status">
    {{ mutation.message.value }}
  </p>
  <p v-if="mutation.uncertain.value" class="feedback warning" role="alert">
    上次操作结果尚未确认，输入和其他提交已锁定。可查询状态，或用原请求重试。
    <el-button
      :disabled="mutation.busy.value"
      data-testid="mutation-retry"
      @click="mutation.execute()"
      >用原请求重试</el-button
    >
  </p>
  <div v-if="mutation.error.value" class="mutation-error">
    <ApiFeedback
      :error="mutation.error.value"
      :resources="resources"
      test-id="mutation-error"
      @retry="mutation.uncertain.value ? mutation.execute() : $emit('refresh')"
    />
    <p v-if="!mutation.uncertain.value" class="caption">
      请核对最新状态与输入后重新操作。
    </p>
  </div>
  <template v-if="mutation.refreshError.value">
    <p class="feedback warning">
      更新状态未完成，请刷新查询；已确认的操作不会再次提交。
    </p>
    <ApiFeedback
      :error="mutation.refreshError.value"
      test-id="mutation-refresh-error"
      @retry="$emit('refresh')"
    />
  </template>
</template>
