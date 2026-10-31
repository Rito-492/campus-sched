<script setup lang="ts">
import type { Impact, Risk, ResourceView } from '../types/api'
import PlanSummary from './PlanSummary.vue'
withDefaults(
  defineProps<{
    impact: Impact
    currentRisk?: Risk
    resources?: ResourceView[]
    historical?: boolean
  }>(),
  { resources: () => [] },
)
</script>
<template>
  <section>
    <h2>安排差异与影响</h2>
    <p v-if="currentRisk === 'AT_RISK'" class="feedback warning">
      原资源当前存在故障风险。改期失败会保留原安排，但不会消除该风险。
    </p>
    <p v-if="!currentRisk" class="feedback warning">
      当前资源风险尚未加载，请刷新活动状态；历史快照不能代替实时风险。
    </p>
    <div class="detail-grid plan-comparison">
      <div class="comparison-side">
        <h3>
          {{ historical ? '本次改期前安排（历史快照）' : '原安排（预览快照）' }}
        </h3>
        <PlanSummary :plan="impact.oldPlan" :resources="resources" />
      </div>
      <div class="comparison-side target">
        <h3>申请的新安排</h3>
        <PlanSummary
          :plan="impact.newPlan"
          :resources="resources"
          :changed-fields="impact.changedFields"
        />
        <p class="caption">浅色标注变化字段；申请中的目标尚未成为有效安排。</p>
      </div>
    </div>
    <div class="impact-grid">
      <article>
        <h3>资源</h3>
        <p>
          释放 {{ impact.releasedResourceIds.length }} / 新增
          {{ impact.acquiredResourceIds.length }} / 保留
          {{ impact.retainedResourceIds.length }}
        </p>
        <p
          v-for="(ids, label) in {
            释放: impact.releasedResourceIds,
            新增: impact.acquiredResourceIds,
            保留: impact.retainedResourceIds,
          }"
          :key="label"
        >
          {{ label }}：{{
            ids
              .map((id) => resources.find((r) => r.id === id)?.name ?? id)
              .join('、') || '无'
          }}
        </p>
      </article>
      <article>
        <h3>审批</h3>
        <p>需要重新审批及资源确认</p>
      </article>
      <article>
        <h3>工作人员</h3>
        <p>{{ impact.requiredStaffUserIds.length }} 人需确认</p>
        <p class="caption">仅指定工作人员本人确认，报名人员无需确认。</p>
      </article>
      <article>
        <h3>报名与通知</h3>
        <p>{{ impact.registeredCount }} 人已报名</p>
        <p>通知接收人仅按当前权限展示</p>
      </article>
    </div>
  </section>
</template>
