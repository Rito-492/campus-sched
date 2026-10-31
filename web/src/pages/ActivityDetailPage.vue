<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { useRoute } from 'vue-router'
import { api } from '../api'
import { ApiError, type Envelope } from '../api/client'
import type { ActivityView, ResourceView, CheckResult } from '../types/api'
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
const route = useRoute(),
  session = useSession(),
  item = ref<ActivityView | null>(null),
  resources = ref<ResourceView[]>([])
const loading = ref(false),
  busy = ref(false),
  error = ref<unknown>(null),
  actionError = ref<unknown>(null),
  message = ref(''),
  comment = ref(''),
  check = ref<CheckResult | null>(null),
  uncertain = ref(false)
const owner = computed(
  () => item.value?.ownerId === session.actor?.id && isApplicant(session.actor),
)
const registrationReason = computed(() =>
  item.value ? registrationBlock(session.actor, item.value) : '',
)
const future = computed(
  () => !!item.value && Date.parse(item.value.input.plan.startAt) > Date.now(),
)
let pending: { run: () => Promise<Envelope<unknown>> } | null = null
async function load() {
  loading.value = true
  error.value = null
  item.value = null
  try {
    item.value = (await api.activity(String(route.params.id))).data
    resources.value = (await api.resources()).data
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}
async function execute(command?: { run: () => Promise<Envelope<unknown>> }) {
  if (busy.value || (uncertain.value && command)) return
  if (command) pending = command
  if (!pending) return
  busy.value = true
  actionError.value = null
  message.value = ''
  try {
    await pending.run()
    pending = null
    uncertain.value = false
    message.value = '操作已由服务端确认，正在刷新当前状态。'
    await load()
  } catch (e) {
    actionError.value = e
    uncertain.value =
      !(e instanceof ApiError) || e.status === 0 || e.status >= 500
    if (e instanceof ApiError && e.status === 409) await load()
  } finally {
    busy.value = false
  }
}
async function preview() {
  if (!item.value || busy.value) return
  pending = null
  check.value = null
  busy.value = true
  actionError.value = null
  try {
    check.value = (
      await api.preview(item.value.id, {
        baseVersionNo: item.value.versionNo,
        targetPlan: item.value.input.plan,
      })
    ).data
  } catch (e) {
    actionError.value = e
  } finally {
    busy.value = false
  }
}
onMounted(load)
</script>
<template>
  <section class="page-heading">
    <RouterLink to="/activities">← 活动工作台</RouterLink>
    <h1>{{ item?.input.title || '活动详情' }}</h1>
    <p class="muted">当前安排、审批与报名状态以服务端最新记录为准。</p>
  </section>
  <ApiFeedback :error="error" :loading="loading" @retry="load" />
  <template v-if="item">
    <p v-if="item.resourceRisk === 'AT_RISK'" class="feedback warning">
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
        <p v-if="message" class="feedback success" role="status">
          {{ message }}
        </p>
        <ApiFeedback
          :error="actionError"
          @retry="pending ? execute() : preview()"
        />
        <p v-if="uncertain" class="feedback warning">
          操作结果尚未确认。请刷新核对，或使用上方“重试”重放原请求。
        </p>
        <el-button :disabled="loading || busy" @click="load"
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
    <section class="panel">
      <h2>办理活动</h2>
      <p class="muted">
        草稿 → 待批准 → 待资源确认 → 已预约 → 开放报名。业务批准不会占用资源。
      </p>
      <div class="toolbar">
        <el-button
          v-if="owner && item.status === 'DRAFT'"
          :disabled="busy || uncertain"
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
          v-if="owner && ['SCHEDULED', 'OPEN'].includes(item.status) && future"
          :to="`/activities/${item.id}/change`"
          >改期工作台</RouterLink
        >
      </div>
      <ConflictList v-if="check" :conflicts="check.conflicts" />
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
  </template>
</template>
