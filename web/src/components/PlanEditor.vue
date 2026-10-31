<script setup lang="ts">
import { computed } from 'vue'
import type { Identity, Plan, ResourceView } from '../types/api'
import { shanghaiInput, shanghaiLocal } from '../domain/format'
const props = withDefaults(
  defineProps<{
    modelValue: Plan
    resources: ResourceView[]
    identity?: Identity
    disabled?: boolean
    prefix?: string
  }>(),
  { prefix: 'plan' },
)
const emit = defineEmits<{ 'update:modelValue': [value: Plan] }>()
const venues = computed(() => props.resources.filter((r) => r.kind === 'VENUE'))
const equipment = computed(() =>
  props.resources.filter((r) => r.kind === 'EQUIPMENT'),
)
const allowed = (r: ResourceView) =>
  !props.identity ||
  (props.identity === 'TEACHER' ? r.allowTeacher : r.allowStudent)
function update(patch: Partial<Plan>) {
  emit('update:modelValue', { ...props.modelValue, ...patch })
}
function need(index: number, patch: Partial<Plan['equipment'][number]>) {
  update({
    equipment: props.modelValue.equipment.map((n, i) =>
      i === index ? { ...n, ...patch } : n,
    ),
  })
}
</script>
<template>
  <el-form label-position="top" :disabled="disabled" class="plan-editor">
    <p class="muted">
      北京时间 · 活动30分钟～6小时，包含布撤场的占用须在同一天。
    </p>
    <div class="form-grid">
      <el-form-item label="开始时间" :for="`${prefix}-start`"
        ><input
          :id="`${prefix}-start`"
          type="datetime-local"
          :disabled="disabled"
          :value="shanghaiLocal(modelValue.startAt)"
          :data-testid="`${prefix}-start`"
          @input="
            update({
              startAt: shanghaiInput(($event.target as HTMLInputElement).value),
            })
          "
      /></el-form-item>
      <el-form-item label="结束时间" :for="`${prefix}-end`"
        ><input
          :id="`${prefix}-end`"
          type="datetime-local"
          :disabled="disabled"
          :value="shanghaiLocal(modelValue.endAt)"
          :data-testid="`${prefix}-end`"
          @input="
            update({
              endAt: shanghaiInput(($event.target as HTMLInputElement).value),
            })
          "
      /></el-form-item>
      <el-form-item label="布场（分钟）"
        ><el-input-number
          :model-value="modelValue.setupMinutes"
          :min="0"
          :max="120"
          aria-label="布场分钟"
          @update:model-value="update({ setupMinutes: Number($event) })"
      /></el-form-item>
      <el-form-item label="撤场（分钟）"
        ><el-input-number
          :model-value="modelValue.teardownMinutes"
          :min="0"
          :max="120"
          aria-label="撤场分钟"
          @update:model-value="update({ teardownMinutes: Number($event) })"
      /></el-form-item>
    </div>
    <el-form-item label="场地">
      <el-select
        :model-value="modelValue.venueId"
        aria-label="场地"
        :data-testid="`${prefix}-venue`"
        @update:model-value="update({ venueId: $event })"
      >
        <el-option
          v-for="r in venues"
          :key="r.id"
          :value="r.id"
          :disabled="!allowed(r)"
          :label="`${r.name} · ${r.capacity}人${allowed(r) ? '' : ' · 当前身份不可申请'}`"
        />
      </el-select>
    </el-form-item>
    <h3>设备需求</h3>
    <div v-for="(n, i) in modelValue.equipment" :key="i" class="toolbar">
      <el-select
        :model-value="n.resourceId"
        aria-label="设备"
        @update:model-value="need(i, { resourceId: $event })"
        ><el-option
          v-for="r in equipment"
          :key="r.id"
          :value="r.id"
          :disabled="!allowed(r)"
          :label="`${r.name} · 库存${r.quantity}${allowed(r) ? '' : ' · 当前身份不可申请'}`"
      /></el-select>
      <el-input-number
        :model-value="n.quantity"
        :min="1"
        aria-label="设备数量"
        @update:model-value="need(i, { quantity: Number($event) })"
      />
      <el-button
        @click="
          update({
            equipment: modelValue.equipment.filter((_, index) => index !== i),
          })
        "
        >移除设备</el-button
      >
    </div>
    <el-button
      @click="
        update({
          equipment: [...modelValue.equipment, { resourceId: '', quantity: 1 }],
        })
      "
      >添加设备</el-button
    >
    <p
      v-for="id in [
        modelValue.venueId,
        ...modelValue.equipment.map((n) => n.resourceId),
      ].filter(Boolean)"
      :key="id"
      class="caption"
    >
      {{ resources.find((r) => r.id === id)?.name ?? id }}：{{
        !resources.some((r) => r.id === id)
          ? '资源信息未返回，请刷新核对'
          : allowed(resources.find((r) => r.id === id)!)
            ? '最终申请资格以服务端检查为准'
            : '不允许当前身份申请，请更换此资源'
      }}
    </p>
  </el-form>
</template>
