<script setup lang="ts">
import { computed, onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { api } from '../api'
import { ApiError, type Envelope } from '../api/client'
import type {
  ActivityView,
  ResourceView,
  CheckResult,
  Conflict,
  AlternativesView,
  Plan,
} from '../types/api'
import { useSession } from '../stores/session'
import {
  isApplicant,
  mayApprove,
  mayReserve,
  registrationBlock,
} from '../domain/access'
import { formatTime, statusLabels } from '../domain/format'
import ApiFeedback from '../components/ApiFeedback.vue'
import ConflictList from '../components/ConflictList.vue'
import DraftEditor from '../components/DraftEditor.vue'
import ActivityRecords from '../components/ActivityRecords.vue'
import MutationFeedback from '../components/MutationFeedback.vue'
import { useMutation } from '../composables/useMutation'
import AlternativesPanel from '../components/AlternativesPanel.vue'
const route = useRoute(),
  session = useSession(),
  item = ref<ActivityView | null>(null),
  resources = ref<ResourceView[]>([])
const loading = ref(false),
  previewBusy = ref(false),
  error = ref<unknown>(null),
  actionError = ref<unknown>(null),
  comment = ref(''),
  check = ref<CheckResult | null>(null),
  editing = ref(false),
  draftLocked = ref(false),
  truncated = ref(false)
const alternatives = ref<AlternativesView | null>(null),
  seedPlan = ref<Plan | undefined>()
const mutation = useMutation({
  confirmed: async () => {
    check.value = null
    await load(true)
  },
  conflict: () => load(true),
})
const uncertain = mutation.uncertain
const busy = computed(
  () =>
    mutation.busy.value ||
    previewBusy.value ||
    loading.value ||
    editing.value ||
    !!error.value,
)
const owner = computed(
  () => item.value?.ownerId === session.actor?.id && isApplicant(session.actor),
)
const registrationReason = computed(() =>
  item.value ? registrationBlock(session.actor, item.value) : '',
)
const future = computed(
  () => !!item.value && Date.parse(item.value.input.plan.startAt) > Date.now(),
)
async function load(rethrow = false) {
  check.value = null
  alternatives.value = null
  loading.value = true
  error.value = null
  try {
    const [a, r] = await Promise.all([
      api.activity(String(route.params.id)),
      api.resources(),
    ])
    item.value = a.data
    resources.value = r.data
    truncated.value = !!r.truncated
  } catch (e) {
    error.value = e
    if (rethrow) throw e
  } finally {
    loading.value = false
  }
}
async function execute(command?: { run: () => Promise<Envelope<unknown>> }) {
  if (!busy.value) await mutation.execute(command)
}
watch(mutation.error, (e) => {
  if (
    e instanceof ApiError &&
    e.status === 409 &&
    e.details &&
    typeof e.details === 'object' &&
    'conflicts' in e.details &&
    Array.isArray(e.details.conflicts)
  )
    check.value = {
      feasible: false,
      conflicts: e.details.conflicts as Conflict[],
    }
})
async function preview() {
  if (!item.value || busy.value) return
  check.value = null
  alternatives.value = null
  previewBusy.value = true
  actionError.value = null
  try {
    check.value = (
      await api.preview(item.value.id, {
        baseVersionNo: item.value.versionNo,
        targetPlan: item.value.input.plan,
      })
    ).data
    if (!check.value.feasible)
      alternatives.value = (
        await api.alternatives(item.value.id, {
          baseVersionNo: item.value.versionNo,
          targetPlan: item.value.input.plan,
        })
      ).data
  } catch (e) {
    actionError.value = e
    if (e instanceof ApiError && e.status === 409) await load()
  } finally {
    previewBusy.value = false
  }
}
function selectAlternative(index: number) {
  if (
    busy.value ||
    uncertain.value ||
    !owner.value ||
    item.value?.status !== 'DRAFT'
  )
    return
  const plan = alternatives.value?.items[index]?.plan
  if (plan) {
    seedPlan.value = plan
    editing.value = true
  }
}
function toggleDraftEditor() {
  seedPlan.value = undefined
  editing.value = !editing.value
}
function draftSaved(saved: ActivityView) {
  item.value = saved
  editing.value = false
  draftLocked.value = false
  check.value = null
  alternatives.value = null
}
onMounted(load)
onBeforeRouteLeave(
  (to) =>
    to.path === '/login' ||
    !uncertain.value ||
    window.confirm('操作结果尚未确认。离开后请先核对活动状态，确定离开？'),
)
</script>
<template>
  <section class="page-heading">
    <RouterLink to="/activities">← 活动工作台</RouterLink>
    <h1>{{ item?.input.title || '活动详情' }}</h1>
    <p class="muted">当前安排、审批与报名状态以服务端最新记录为准。</p>
  </section>
  <ApiFeedback
    :error="error"
    :loading="loading"
    :truncated="truncated"
    @retry="load()"
  />
  <template v-if="item">
    <p
      v-if="item.resourceRisk === 'AT_RISK'"
      class="feedback warning"
      data-testid="risk-banner"
    >
      ⚠ 当前资源受故障影响。原安排仍保留，请联系负责人调整资源。
    </p>
    <div class="detail-grid">
      <section class="panel">
        <div class="row">
          <h2>当前安排</h2>
          <span class="badge">{{ statusLabels[item.status] }}</span>
        </div>
        <p>{{ item.input.description || '暂无简介' }}</p>
        <dl class="summary">
          <dt>活动时间</dt>
          <dd>
            {{ formatTime(item.input.plan.startAt) }} —
            {{ formatTime(item.input.plan.endAt) }}
          </dd>
          <dt>布场 / 撤场</dt>
          <dd>
            {{ item.input.plan.setupMinutes }} /
            {{ item.input.plan.teardownMinutes }} 分钟
          </dd>
          <dt>场地</dt>
          <dd>
            {{
              resources.find((r) => r.id === item?.input.plan.venueId)?.name ??
              '资源信息未加载'
            }}
          </dd>
          <dt>设备</dt>
          <dd>
            <p v-for="need in item.input.plan.equipment" :key="need.resourceId">
              {{
                resources.find((r) => r.id === need.resourceId)?.name ??
                need.resourceId
              }}
              × {{ need.quantity }}
            </p>
            <span v-if="!item.input.plan.equipment.length">无设备需求</span>
          </dd>
          <dt>有效版本</dt>
          <dd data-testid="current-version">
            {{ item.effectiveVersionNo ?? '尚未生效' }}
          </dd>
        </dl>
        <RouterLink
          v-if="item.activeChangeId"
          :to="`/changes/${item.activeChangeId}`"
          >查看改期进度（报名暂停）</RouterLink
        >
        <MutationFeedback
          :mutation="mutation"
          :resources="resources"
          @refresh="load()"
        />
        <ApiFeedback
          :error="actionError"
          :resources="resources"
          test-id="preview-error"
          @retry="preview"
        />
        <p v-if="uncertain" class="feedback warning">
          操作结果尚未确认。请刷新核对，或使用上方“重试”重放原请求。
        </p>
        <el-button
          :disabled="
            loading || mutation.busy.value || previewBusy || draftLocked
          "
          @click="load()"
          >刷新状态</el-button
        >
      </section>
      <section class="panel">
        <h2>报名参与</h2>
        <p class="big-number">
          {{ item.registrationCount }}
          <small>/ {{ item.input.registrationCapacity }} 人</small>
        </p>
        <p>
          {{
            item.myRegistrationStatus === 'ACTIVE' ? '你已报名' : '你尚未报名'
          }}
        </p>
        <p v-if="registrationReason" class="muted">{{ registrationReason }}</p>
        <el-button
          type="primary"
          :disabled="!!registrationReason || busy || uncertain"
          data-testid="registration-submit"
          @click="
            execute(
              api.registration(item.id, {
                action:
                  item.myRegistrationStatus === 'ACTIVE' ? 'WITHDRAW' : 'JOIN',
                expectedVersionNo: item.effectiveVersionNo ?? item.versionNo,
              }),
            )
          "
          >{{
            item.myRegistrationStatus === 'ACTIVE' ? '退出报名' : '报名参加'
          }}</el-button
        >
      </section>
    </div>
    <div v-if="owner && (item.status === 'DRAFT' || editing)" class="toolbar">
      <el-button
        :disabled="uncertain || mutation.busy.value || draftLocked || loading"
        data-testid="draft-edit"
        @click="toggleDraftEditor"
        >{{ editing ? '关闭草稿编辑' : '编辑草稿' }}</el-button
      >
    </div>
    <DraftEditor
      v-if="editing && session.actor"
      :activity="item"
      :actor="session.actor"
      :resources="resources"
      :disabled="loading || !!error"
      :refresh="() => load(true)"
      :initial-plan="seedPlan"
      @locked="draftLocked = $event"
      @saved="draftSaved"
    />
    <section class="panel">
      <h2>办理活动</h2>
      <p class="muted">
        草稿 → 待批准 → 待资源确认 → 已预约 → 开放报名。业务批准不会占用资源。
      </p>
      <div class="toolbar">
        <el-button
          v-if="owner && item.status === 'DRAFT'"
          :disabled="busy || uncertain"
          data-testid="activity-preview"
          @click="preview"
          >检查资源</el-button
        >
        <el-button
          v-if="owner && item.status === 'DRAFT'"
          type="primary"
          :disabled="busy || uncertain"
          data-testid="activity-submit"
          @click="
            execute(
              api.submit(item.id, { expectedLockVersion: item.lockVersion }),
            )
          "
          >提交审批</el-button
        >
        <el-button
          v-if="owner && item.status === 'SCHEDULED'"
          type="primary"
          :disabled="
            busy || uncertain || !future || item.resourceRisk === 'AT_RISK'
          "
          data-testid="activity-publish"
          @click="
            execute(
              api.publish(item.id, { expectedLockVersion: item.lockVersion }),
            )
          "
          >发布活动</el-button
        >
        <el-button
          v-if="
            item.status === 'APPROVED_UNRESERVED' &&
            mayReserve(session.actor, item)
          "
          type="primary"
          :disabled="busy || uncertain"
          data-testid="resource-confirm"
          @click="
            execute(
              api.reserve(item.id, { expectedLockVersion: item.lockVersion }),
            )
          "
          >确认联合预约</el-button
        >
        <RouterLink
          v-if="
            owner &&
            ['SCHEDULED', 'OPEN'].includes(item.status) &&
            future &&
            !item.activeChangeId
          "
          :to="`/activities/${item.id}/change`"
          data-testid="change-entry"
          >发起改期</RouterLink
        >
      </div>
      <ConflictList v-if="check" :conflicts="check.conflicts" />
      <template v-if="alternatives"
        ><p class="caption">
          选择候选会填入草稿编辑器，保存修改后再检查资源及提交审批；选择不会预约。
        </p>
        <AlternativesPanel
          :alternatives="alternatives"
          :resources="resources"
          :disabled="busy || uncertain"
          @select="selectAlternative"
      /></template>
      <template
        v-if="
          (owner && item.status !== 'CANCELLED') ||
          (item.status === 'PENDING_APPROVER' &&
            mayApprove(session.actor, item, resources))
        "
        ><label for="comment">原因或审批意见（拒绝、撤回、取消必填）</label
        ><el-input
          id="comment"
          v-model="comment"
          type="textarea"
          :rows="2"
          maxlength="1000"
          :disabled="busy || uncertain"
        />
        <div class="toolbar">
          <template
            v-if="
              item.status === 'PENDING_APPROVER' &&
              mayApprove(session.actor, item, resources)
            "
            ><el-button
              type="primary"
              :disabled="busy || uncertain"
              data-testid="approver-approve"
              @click="
                execute(
                  api.approve(item.id, {
                    expectedLockVersion: item.lockVersion,
                    decision: 'APPROVE',
                    comment,
                  }),
                )
              "
              >批准</el-button
            ><el-button
              :disabled="busy || uncertain || !comment.trim()"
              @click="
                execute(
                  api.approve(item.id, {
                    expectedLockVersion: item.lockVersion,
                    decision: 'REJECT',
                    comment,
                  }),
                )
              "
              >驳回</el-button
            ></template
          >
          <el-button
            v-if="owner && item.status === 'PENDING_APPROVER'"
            :disabled="busy || uncertain || !comment.trim()"
            @click="
              execute(
                api.withdraw(item.id, {
                  expectedLockVersion: item.lockVersion,
                  reason: comment,
                }),
              )
            "
            >撤回为新草稿</el-button
          >
          <el-popconfirm
            v-if="owner && future"
            title="取消后不可恢复，确定取消？"
            @confirm="
              execute(
                api.cancel(item.id, {
                  expectedLockVersion: item.lockVersion,
                  reason: comment,
                }),
              )
            "
            ><template #reference
              ><el-button
                type="danger"
                plain
                :disabled="busy || uncertain || !comment.trim()"
                >取消活动</el-button
              ></template
            ></el-popconfirm
          >
        </div></template
      >
    </section>
    <ActivityRecords
      :activity="item"
      :actor="session.actor"
      :resources="resources"
    />
  </template>
</template>
