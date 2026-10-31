<script setup lang="ts">
import { computed, ref } from 'vue'
import type {
  Actor,
  ActivityView,
  ResourceView,
  ChangeView,
  AlternativesView,
} from '../types/api'
import PlanEditor from '../components/PlanEditor.vue'
import AlternativesPanel from '../components/AlternativesPanel.vue'
import ImpactPanel from '../components/ImpactPanel.vue'
import ChangeTimeline from '../components/ChangeTimeline.vue'
import ChangeActions from '../components/ChangeActions.vue'
import ConflictList from '../components/ConflictList.vue'
import { changeLabels, pendingChange } from '../domain/change'
// UI_FIXTURE_ONLY：独立的外观样例。无API、无会话、无写操作。
const props = defineProps<{
  actor: Actor
  activity: ActivityView
  resources: ResourceView[]
  detail?: boolean
}>()
const target = ref(
  JSON.parse(
    JSON.stringify({
      ...props.activity.input.plan,
      venueId: props.resources[1].id,
      startAt: '2030-10-21T14:00:00+08:00',
      endAt: '2030-10-21T16:00:00+08:00',
    }),
  ),
)
const status = ref<ChangeView['status']>(
  props.actor.permissions.includes('RESOURCE_CONFIRM')
    ? 'PENDING_RESOURCE'
    : props.actor.roles.includes('APPROVER')
      ? 'PENDING_APPROVER'
      : 'AWAITING_CONFIRMATIONS',
)
const count = ref(5)
const alternatives = computed<AlternativesView>(() => ({
  items: Array.from({ length: count.value }, (_, i) => ({
    plan: {
      ...target.value,
      startAt: `2030-10-21T${14 + Math.floor(i / 2)}:${i % 2 ? '30' : '00'}:00+08:00`,
      endAt: `2030-10-21T${16 + Math.floor(i / 2)}:${i % 2 ? '30' : '00'}:00+08:00`,
    },
    timeShiftMinutes: i * 30,
    venueChanged: true,
    replacedEquipmentLines: 0,
    explanation: '固定组件样例，尚未检查真实可行性。',
  })),
  evaluatedCount: 36,
  truncated: false,
  offsetsMinutes: [0, -30, 30, -60, 60],
  maxEvaluations: 300,
}))
const change = computed<ChangeView>(() => ({
  id: 'preview-change',
  activityId: props.activity.id,
  baseVersionNo: props.activity.versionNo,
  targetPlan: target.value,
  reason: '原场地故障，需要调整活动时间与场地。',
  status: status.value,
  impact: {
    oldPlan: props.activity.input.plan,
    newPlan: target.value,
    releasedResourceIds: [props.activity.input.plan.venueId],
    acquiredResourceIds: [target.value.venueId],
    retainedResourceIds: [],
    requiredStaffUserIds: ['示例工作人员'],
    registeredUserIds: [],
    registeredCount: 12,
    notificationUserIds: [],
    approvalReset: true,
    originalResourceRisk: 'AT_RISK',
    changedFields: ['startAt', 'endAt', 'venueId'],
  },
  confirmations: [
    {
      userId: '示例工作人员',
      decision: status.value === 'AWAITING_CONFIRMATIONS' ? null : 'APPROVE',
      comment: '已核对本人安排。',
      decidedAt:
        status.value === 'AWAITING_CONFIRMATIONS'
          ? null
          : '2030-10-19T09:00:00+08:00',
    },
  ],
  approverDecision: ['PENDING_RESOURCE', 'CONFLICTED', 'APPLIED'].includes(
    status.value,
  )
    ? {
        actorId: '示例批准人',
        decision: 'APPROVE',
        comment: '同意调整。',
        decidedAt: '2030-10-19T10:00:00+08:00',
      }
    : null,
  resourceDecision:
    status.value === 'APPLIED'
      ? {
          actorId: '示例资源管理员',
          decision: 'APPROVE',
          comment: '示例资源确认记录。',
          decidedAt: '2030-10-19T11:00:00+08:00',
        }
      : null,
  lastConflicts:
    status.value === 'CONFLICTED'
      ? [
          {
            code: 'VENUE_OCCUPIED',
            resourceId: target.value.venueId,
            resourceName: props.resources[1].name,
            startsAt: target.value.startAt,
            endsAt: target.value.endAt,
            required: 1,
            available: 0,
            message: '此时段场地已被其他活动占用（固定样例）。',
          },
        ]
      : [],
  appliedVersionNo: status.value === 'APPLIED' ? 3 : null,
  createdBy: props.activity.ownerId,
  createdAt: '2030-10-19T08:00:00+08:00',
  updatedAt: '2030-10-19T11:00:00+08:00',
}))
const activityForActions = computed(() => ({
  ...props.activity,
  activeChangeId: pendingChange(status.value) ? change.value.id : null,
}))
</script>
<template>
  <section class="page-heading">
    <h1>{{ detail ? '改期进度' : '改期工作台' }}</h1>
    <p class="muted">
      组件外观预览 · 所有检查结果与状态均为固定样例，不代表真实业务操作。
    </p>
  </section>
  <template v-if="!detail">
    <section class="panel">
      <h2>01 / 调整目标安排</h2>
      <PlanEditor
        v-model="target"
        :resources="resources"
        :identity="actor.identity"
        prefix="preview-change"
      />
      <p class="feedback warning">
        编辑只改变本地展示；真实资源检查和创建在预览模式关闭。
      </p>
      <el-button data-testid="change-preview" disabled
        >预览最终目标（需真实服务）</el-button
      >
    </section>
    <section class="panel">
      <div class="toolbar">
        <label for="preview-candidates">查看候选数量样例</label
        ><select
          id="preview-candidates"
          v-model="count"
          data-testid="preview-candidates"
        >
          <option :value="5">5项方案</option>
          <option :value="0">无方案</option>
        </select>
      </div>
      <AlternativesPanel
        :alternatives="alternatives"
        :resources="resources"
        @select="
          target = JSON.parse(JSON.stringify(alternatives.items[$event].plan))
        "
      />
    </section>
  </template>
  <section class="panel">
    <ImpactPanel
      :impact="change.impact"
      current-risk="AT_RISK"
      :resources="resources"
      :historical="detail"
    /><el-button
      v-if="!detail"
      data-testid="change-create"
      type="primary"
      disabled
      >创建改期（预览模式不可提交）</el-button
    >
  </section>
  <template v-if="detail"
    ><section class="panel">
      <div class="toolbar">
        <label for="preview-change-status">查看进度样例</label
        ><select
          id="preview-change-status"
          v-model="status"
          data-testid="preview-change-status"
        >
          <option
            v-for="(label, value) in changeLabels"
            :key="value"
            :value="value"
          >
            {{ label }}
          </option>
        </select>
      </div>
      <ChangeTimeline :change="change" /><ConflictList
        v-if="change.lastConflicts.length"
        :conflicts="change.lastConflicts"
      />
    </section>
    <ChangeActions
      :actor="actor"
      :activity="activityForActions"
      :change="change"
      :resources="resources"
      preview
  /></template>
</template>
