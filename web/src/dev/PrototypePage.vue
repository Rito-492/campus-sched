<script setup lang="ts">
import { ref } from 'vue'
import type {
  AlternativesView,
  CalendarEntry,
  ChangeView,
  Conflict,
  Impact,
  Plan,
  ResourceView,
} from '../types/api'
import ResourceCalendar from '../components/ResourceCalendar.vue'
import ConflictList from '../components/ConflictList.vue'
import AlternativesPanel from '../components/AlternativesPanel.vue'
import ImpactPanel from '../components/ImpactPanel.vue'
import ChangeTimeline from '../components/ChangeTimeline.vue'
// UI_FIXTURE_ONLY：仅通过 import.meta.env.DEV 路由加载，不访问 API，不建立会话。
const selected = ref<number | null>(null)
const plan: Plan = {
  startAt: '2030-10-20T10:00:00+08:00',
  endAt: '2030-10-20T11:00:00+08:00',
  setupMinutes: 30,
  teardownMinutes: 30,
  venueId: '原型场地 A',
  equipment: [{ resourceId: '原型投影设备', quantity: 2 }],
}
const resources: ResourceView[] = [
  {
    id: plan.venueId,
    kind: 'VENUE',
    name: '原型 · 学术报告厅 A',
    category: 'VENUE',
    capacity: 80,
    quantity: 1,
    openFrom: '08:00',
    openTo: '22:00',
    allowTeacher: true,
    allowStudent: true,
    createdAt: plan.startAt,
  },
]
const entries: CalendarEntry[] = [
  {
    resourceId: plan.venueId,
    startsAt: '2030-10-20T09:30:00+08:00',
    endsAt: plan.startAt,
    quantity: 1,
    activityId: null,
    label: '布场',
    risk: 'NORMAL',
    segment: 'SETUP',
  },
  {
    resourceId: plan.venueId,
    startsAt: plan.startAt,
    endsAt: plan.endAt,
    quantity: 1,
    activityId: null,
    label: '示例活动',
    risk: 'AT_RISK',
    segment: 'EVENT',
  },
]
const conflicts: Conflict[] = [
  {
    code: 'EQUIPMENT_SHORTAGE',
    resourceId: '原型投影设备',
    resourceName: '投影设备组 A',
    startsAt: '2030-10-20T15:00:00+08:00',
    endsAt: '2030-10-20T16:00:00+08:00',
    required: 2,
    available: 1,
    message: '该时段设备缺少1件，请更换设备或调整时间。',
  },
]
const alternatives: AlternativesView = {
  items: [0, 30, -30].map((offset, i) => ({
    plan: {
      ...plan,
      venueId: '原型场地 B',
      startAt: `2030-10-20T${i === 2 ? '14:30' : i === 1 ? '15:30' : '15:00'}:00+08:00`,
      endAt: `2030-10-20T${i === 2 ? '15:30' : i === 1 ? '16:30' : '16:00'}:00+08:00`,
    },
    timeShiftMinutes: offset,
    venueChanged: true,
    replacedEquipmentLines: 1,
    explanation: '更换场地和设备，保留活动时长。',
  })),
  evaluatedCount: 36,
  truncated: false,
  offsetsMinutes: [0, -30, 30],
  maxEvaluations: 300,
}
function impact(index = 0): Impact {
  return {
    oldPlan: plan,
    newPlan: alternatives.items[index].plan,
    releasedResourceIds: ['原型场地 A'],
    acquiredResourceIds: ['原型场地 B'],
    retainedResourceIds: ['原型投影设备'],
    requiredStaffUserIds: ['工作人员甲'],
    registeredUserIds: [],
    registeredCount: 12,
    notificationUserIds: [],
    approvalReset: true,
    originalResourceRisk: 'AT_RISK',
    changedFields: ['startAt', 'endAt', 'venueId'],
  }
}
const change: ChangeView = {
  id: '原型申请',
  activityId: '原型活动',
  baseVersionNo: 1,
  targetPlan: alternatives.items[0].plan,
  reason: '资源发生故障',
  status: 'AWAITING_CONFIRMATIONS',
  impact: impact(),
  confirmations: [
    { userId: '工作人员甲', decision: null, comment: '', decidedAt: null },
  ],
  approverDecision: null,
  resourceDecision: null,
  lastConflicts: [],
  appliedVersionNo: null,
  createdBy: '原型申请人',
  createdAt: plan.startAt,
  updatedAt: plan.startAt,
}
</script>
<template>
  <main id="main" class="prototype-page">
    <div class="feedback warning prototype-banner" role="status">
      <strong>演示数据 / 未连接后端</strong> ·
      仅组件开发原型，所有选择只影响当前展示，不提交业务操作。
    </div>
    <section class="page-heading">
      <span class="eyebrow">UI-01 · 开发原型</span>
      <h1>把变更的每一步，解释清楚</h1>
      <p class="muted">资源日历 → 冲突与备选 → 差异及影响 → 确认与结果</p>
    </section>
    <section class="panel">
      <h2>01 / 资源日历</h2>
      <ResourceCalendar
        date="2030-10-20"
        :resources="resources"
        :entries="entries"
      />
    </section>
    <section class="panel">
      <h2>02 / 冲突与替代方案</h2>
      <ConflictList :conflicts="conflicts" /><AlternativesPanel
        :alternatives="alternatives"
        @select="selected = $event"
      />
    </section>
    <section class="panel">
      <p v-if="selected !== null" class="feedback success">
        已选择原型方案 {{ selected + 1 }}，仅展示影响，未预约。
      </p>
      <ImpactPanel :impact="impact(selected ?? 0)" current-risk="AT_RISK" />
    </section>
    <section class="panel"><ChangeTimeline :change="change" /></section>
  </main>
</template>
