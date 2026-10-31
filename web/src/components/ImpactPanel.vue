<script setup lang="ts">
import type { Impact, Risk } from '../types/api'
import { formatTime } from '../domain/format'
defineProps<{ impact: Impact; currentRisk: Risk }>()
</script>
<template>
  <section>
    <h2>安排差异与影响</h2>
    <p v-if="currentRisk === 'AT_RISK'" class="feedback warning">
      原资源当前存在故障风险。改期失败会保留原安排，但不会消除该风险。
    </p>
    <div class="detail-grid">
      <div class="comparison-side">
        <h3>当前有效安排</h3>
        <p>
          {{ formatTime(impact.oldPlan.startAt) }} —
          {{ formatTime(impact.oldPlan.endAt) }}
        </p>
        <p>场地：{{ impact.oldPlan.venueId }}</p>
        <p>
          布场 {{ impact.oldPlan.setupMinutes }} / 撤场
          {{ impact.oldPlan.teardownMinutes }} 分钟
        </p>
      </div>
      <div class="comparison-side target">
        <h3>申请的新安排</h3>
        <p>
          {{ formatTime(impact.newPlan.startAt) }} —
          {{ formatTime(impact.newPlan.endAt) }}
        </p>
        <p>场地：{{ impact.newPlan.venueId }}</p>
        <p>
          布场 {{ impact.newPlan.setupMinutes }} / 撤场
          {{ impact.newPlan.teardownMinutes }} 分钟
        </p>
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
      </article>
      <article>
        <h3>审批</h3>
        <p>需要重新审批及资源确认</p>
      </article>
      <article>
        <h3>工作人员</h3>
        <p>{{ impact.requiredStaffUserIds.length }} 人需确认</p>
      </article>
      <article>
        <h3>报名与通知</h3>
        <p>{{ impact.registeredCount }} 人已报名</p>
        <p>通知接收人仅按当前权限展示</p>
      </article>
    </div>
  </section>
</template>
