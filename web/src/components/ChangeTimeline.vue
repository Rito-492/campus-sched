<script setup lang="ts">
import type { ChangeView } from '../types/api'
import { formatTime } from '../domain/format'
defineProps<{ change: ChangeView }>()
const labels = {
  AWAITING_CONFIRMATIONS: '等待工作人员确认',
  PENDING_APPROVER: '等待批准人复审',
  PENDING_RESOURCE: '等待资源确认',
  APPLIED: '改期已生效',
  REJECTED: '已拒绝',
  CONFLICTED: '资源冲突',
  WITHDRAWN: '已撤回',
  CANCELLED_BY_ACTIVITY: '活动取消，申请关闭',
}
</script>
<template>
  <section>
    <h2>确认与结果</h2>
    <p class="badge" data-testid="change-status">{{ labels[change.status] }}</p>
    <ol class="timeline">
      <li>
        <strong>提交改期申请</strong>
        <p>
          {{ formatTime(change.createdAt) }} · 基于版本
          {{ change.baseVersionNo }}
        </p>
      </li>
      <li v-for="c in change.confirmations" :key="c.userId">
        <strong>工作人员 {{ c.userId }}</strong>
        <p>
          {{
            c.decision === 'APPROVE'
              ? '已同意'
              : c.decision === 'REJECT'
                ? '已拒绝'
                : '等待本人确认'
          }}
          {{ c.decidedAt ? formatTime(c.decidedAt) : '' }}
        </p>
        <p>{{ c.comment }}</p>
      </li>
      <li>
        <strong>批准人复审</strong>
        <p>{{ change.approverDecision?.comment || '等待处理' }}</p>
      </li>
      <li>
        <strong>资源确认</strong>
        <p>{{ change.resourceDecision?.comment || '等待处理' }}</p>
      </li>
    </ol>
    <p v-if="change.status === 'APPLIED'" class="feedback success">
      已生效为版本 {{ change.appliedVersionNo }}，请刷新活动安排与日历。
    </p>
    <p
      v-else-if="
        ['REJECTED', 'CONFLICTED', 'WITHDRAWN'].includes(change.status)
      "
      class="feedback warning"
    >
      原安排未被本次改期修改，请继续关注原资源风险。
    </p>
  </section>
</template>
