<script setup lang="ts">
import type { ChangeView } from '../types/api'
import { formatTime } from '../domain/format'
import { changeLabels as labels } from '../domain/change'
defineProps<{ change: ChangeView }>()
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
        <p>申请人 {{ change.createdBy }} · {{ change.reason }}</p>
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
        <template v-if="change.approverDecision"
          ><p>
            {{
              change.approverDecision.decision === 'APPROVE' ? '同意' : '拒绝'
            }}
            · {{ change.approverDecision.actorId }} ·
            {{ formatTime(change.approverDecision.decidedAt) }}
          </p>
          <p>
            {{ change.approverDecision.comment || '未填写补充意见' }}
          </p></template
        >
        <p v-else>尚无复审记录</p>
      </li>
      <li>
        <strong>资源确认</strong>
        <template v-if="change.resourceDecision"
          ><p>
            {{
              change.resourceDecision.decision === 'APPROVE' ? '同意' : '拒绝'
            }}
            · {{ change.resourceDecision.actorId }} ·
            {{ formatTime(change.resourceDecision.decidedAt) }}
          </p>
          <p>
            {{ change.resourceDecision.comment || '未填写补充意见' }}
          </p></template
        >
        <p v-else>尚无资源确认记录</p>
      </li>
      <li>
        <strong>{{ labels[change.status] }}</strong>
        <p>
          最近更新 {{ formatTime(change.updatedAt)
          }}<span v-if="change.appliedVersionNo !== null">
            · 生效版本 {{ change.appliedVersionNo }}</span
          >
        </p>
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
