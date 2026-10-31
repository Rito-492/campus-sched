<script setup lang="ts">
import { computed, ref } from 'vue'
import type { AlternativesView } from '../types/api'
import { formatTime } from '../domain/format'
const props = defineProps<{ alternatives: AlternativesView }>()
defineEmits<{ select: [index: number] }>()
const selected = ref<number[]>([])
const compared = computed(() =>
  props.alternatives.items
    .slice(0, 5)
    .filter((_, i) => selected.value.includes(i)),
)
</script>
<template>
  <section>
    <h2>比较可行方案</h2>
    <p class="muted">
      已检查 {{ alternatives.evaluatedCount }} 个组合 ·
      最多展示5项。选择2～3项比较，选择方案不代表预约。
    </p>
    <p v-if="alternatives.truncated" class="feedback warning">
      搜索已达到预算，仅展示当前搜索范围内的结果。
    </p>
    <p v-if="!alternatives.items.length" class="empty-state">
      当前搜索范围内无可行方案，请调整时间或资源。
    </p>
    <el-checkbox-group v-model="selected" :max="3" class="alternative-grid"
      ><article
        v-for="(item, i) in alternatives.items.slice(0, 5)"
        :key="i"
        class="alternative-card"
      >
        <span class="badge">{{
          i === 0 ? '当前排序首选' : `方案 ${i + 1}`
        }}</span>
        <h3>{{ formatTime(item.plan.startAt) }}</h3>
        <p>时间偏移 {{ item.timeShiftMinutes }} 分钟</p>
        <p>
          {{ item.venueChanged ? '更换场地' : '保留场地' }} · 替换
          {{ item.replacedEquipmentLines }} 项设备
        </p>
        <p class="muted">{{ item.explanation }}</p>
        <el-checkbox :value="i">加入比较</el-checkbox
        ><el-button
          :data-testid="`alternative-${i}`"
          @click="$emit('select', i)"
          >选择并查看影响</el-button
        >
      </article></el-checkbox-group
    >
    <div v-if="compared.length >= 2" class="table-scroll">
      <table>
        <caption>
          方案比较
        </caption>
        <thead>
          <tr>
            <th>维度</th>
            <th v-for="(_, i) in compared" :key="i">所选方案 {{ i + 1 }}</th>
          </tr>
        </thead>
        <tbody>
          <tr>
            <th>时间</th>
            <td v-for="(item, i) in compared" :key="i">
              {{ formatTime(item.plan.startAt) }}
            </td>
          </tr>
          <tr>
            <th>场地</th>
            <td v-for="(item, i) in compared" :key="i">
              {{ item.venueChanged ? '更换' : '保留' }}
            </td>
          </tr>
          <tr>
            <th>设备替换</th>
            <td v-for="(item, i) in compared" :key="i">
              {{ item.replacedEquipmentLines }} 项
            </td>
          </tr>
        </tbody>
      </table>
    </div>
  </section>
</template>
