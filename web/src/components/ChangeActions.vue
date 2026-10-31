<script setup lang="ts">
import { computed, ref } from 'vue'
import type {
  Actor,
  ActivityView,
  ChangeView,
  ResourceView,
  ChangeDecision,
} from '../types/api'
import { changeActions } from '../domain/change'
const props = defineProps<{
  actor: Actor | null
  activity: ActivityView
  change: ChangeView
  resources: ResourceView[]
  disabled?: boolean
  preview?: boolean
}>()
const emit = defineEmits<{
  decision: [kind: 'confirm' | 'approve' | 'reserve', body: ChangeDecision]
  withdraw: [reason: string]
}>()
const comment = ref(''),
  actions = computed(() =>
    changeActions(props.actor, props.activity, props.change, props.resources),
  )
const rejectKind = ref<'confirm' | 'approve' | 'reserve' | null>(null)
const rejectReason = ref('')
const rejecting = computed({
  get: () => rejectKind.value !== null,
  set: (open: boolean) => {
    if (!open) rejectKind.value = null
  },
})
function openReject(kind: 'confirm' | 'approve' | 'reserve') {
  if (!actions.value[kind] || props.disabled || props.preview) return
  rejectReason.value = comment.value
  rejectKind.value = kind
}
function reject() {
  const kind = rejectKind.value
  if (
    !kind ||
    !rejectReason.value.trim() ||
    !actions.value[kind] ||
    props.disabled ||
    props.preview
  )
    return
  emit('decision', kind, {
    decision: 'REJECT',
    comment: rejectReason.value.trim(),
  })
  rejectKind.value = null
}
function decide(
  kind: 'confirm' | 'approve' | 'reserve',
  decision: 'APPROVE' | 'REJECT',
) {
  if (
    !actions.value[kind] ||
    props.disabled ||
    props.preview ||
    (decision === 'REJECT' && !comment.value.trim())
  )
    return
  emit('decision', kind, { decision, comment: comment.value })
}
function withdraw() {
  if (
    actions.value.withdraw &&
    comment.value.trim() &&
    !props.disabled &&
    !props.preview
  )
    emit('withdraw', comment.value)
}
</script>
<template>
  <section class="panel" data-testid="change-actions">
    <h2>处理当前步骤</h2>
    <p class="muted">
      工作人员仅确认本人安排；活动所有者不能批准或复核本人申请。
    </p>
    <p v-if="preview" class="feedback warning">
      演示数据 / 未连接后端。此处不执行确认、审批或撤回。
    </p>
    <template v-if="Object.values(actions).some(Boolean)">
      <label for="change-comment">意见或撤回原因（拒绝、撤回必填）</label
      ><el-input
        id="change-comment"
        v-model="comment"
        type="textarea"
        :rows="3"
        maxlength="1000"
        :disabled="disabled"
        data-testid="change-comment"
      />
      <div class="toolbar">
        <template
          v-for="(label, kind) in {
            confirm: '确认本人安排',
            approve: '批准改期',
            reserve: '确认全部资源并生效',
          }"
          :key="kind"
        >
          <template v-if="actions[kind]"
            ><el-button
              type="primary"
              :disabled="disabled || preview"
              :data-testid="
                {
                  confirm: 'staff-approve',
                  approve: 'approver-approve',
                  reserve: 'change-resource-approve',
                }[kind]
              "
              @click="decide(kind, 'APPROVE')"
              >{{ label }}</el-button
            ><el-button
              :disabled="disabled || preview"
              data-testid="change-reject"
              @click="openReject(kind)"
              >拒绝</el-button
            ></template
          >
        </template>
        <el-popconfirm
          v-if="actions.withdraw"
          title="撤回后本次申请终止，原安排保留。确定撤回？"
          @confirm="withdraw"
          ><template #reference
            ><el-button
              :disabled="disabled || preview || !comment.trim()"
              data-testid="change-withdraw"
              >撤回改期</el-button
            ></template
          ></el-popconfirm
        >
      </div>
    </template>
    <p v-else class="empty-state">
      当前没有需要你处理的步骤。可查看时间线，或刷新最新状态。
    </p>
    <el-dialog
      v-model="rejecting"
      title="拒绝改期"
      width="min(520px, 94vw)"
      :close-on-click-modal="false"
    >
      <p>拒绝后本次申请终止，原安排保留。请说明原因，便于申请人调整。</p>
      <label for="reject-reason">拒绝原因（必填）</label>
      <el-input
        id="reject-reason"
        v-model="rejectReason"
        type="textarea"
        :rows="4"
        maxlength="1000"
        data-testid="reject-reason"
      />
      <template #footer>
        <el-button @click="rejecting = false">返回</el-button>
        <el-button
          type="danger"
          data-testid="reject-confirm"
          :disabled="
            disabled ||
            preview ||
            !rejectReason.trim() ||
            !rejectKind ||
            !actions[rejectKind]
          "
          @click="reject"
          >确认拒绝</el-button
        >
      </template>
    </el-dialog>
  </section>
</template>
