<script setup lang="ts">
import { onBeforeUnmount, onMounted } from 'vue'
import { onBeforeRouteLeave, useRoute, useRouter } from 'vue-router'
import { useSession } from '../stores/session'
import { useChangeWorkspace } from '../composables/useChangeWorkspace'
import ApiFeedback from '../components/ApiFeedback.vue'
import MutationFeedback from '../components/MutationFeedback.vue'
import PlanEditor from '../components/PlanEditor.vue'
import PlanSummary from '../components/PlanSummary.vue'
import ConflictList from '../components/ConflictList.vue'
import AlternativesPanel from '../components/AlternativesPanel.vue'
import ImpactPanel from '../components/ImpactPanel.vue'
const route = useRoute(),
  router = useRouter(),
  session = useSession()
const state = useChangeWorkspace(
  String(route.params.id),
  () => session.actor,
  async (change) => {
    await router.push(`/changes/${change.id}`)
  },
)
const {
  activity,
  resources,
  target,
  reason,
  result,
  loading,
  previewing,
  error,
  previewError,
  errors,
  truncated,
  mutation,
  allowed,
  locked,
  ready,
} = state
onMounted(() => state.load())
onBeforeUnmount(state.invalidate)
onBeforeRouteLeave(
  (to) =>
    to.path === '/login' ||
    !mutation.uncertain.value ||
    window.confirm(
      '改期创建结果尚未确认，离开后请先查询活动的改期进度。确定离开？',
    ),
)
</script>
<template>
  <section class="page-heading">
    <RouterLink :to="`/activities/${route.params.id}`">← 活动详情</RouterLink>
    <h1>改期工作台</h1>
    <p class="muted">
      检查资源、比较方案，再确认影响。预览和选择方案都不会占用资源。
    </p>
  </section>
  <ApiFeedback
    :error="error"
    :loading="loading"
    :truncated="truncated"
    @retry="state.load()"
  />
  <MutationFeedback
    :mutation="mutation"
    :resources="resources"
    @refresh="state.load()"
  />
  <template v-if="activity && target">
    <p
      v-if="activity.resourceRisk === 'AT_RISK'"
      class="feedback warning"
      data-testid="risk-banner"
    >
      当前有效安排存在资源故障风险，发起改期不会立即解除风险。
    </p>
    <p v-if="!allowed" class="feedback warning">
      当前不能发起新的改期：需要本人申请人权限、已预约或开放报名、尚未开始，且没有待处理改期。<RouterLink
        v-if="activity.activeChangeId"
        :to="`/changes/${activity.activeChangeId}`"
        >查看已有改期</RouterLink
      >
    </p>
    <div class="change-workspace">
      <section class="panel">
        <h2>01 / 调整目标安排</h2>
        <p class="caption">
          {{ activity.input.title }} · 基于版本 {{ activity.versionNo }}
        </p>
        <details>
          <summary>查看当前有效安排</summary>
          <PlanSummary :plan="activity.input.plan" :resources="resources" />
        </details>
        <PlanEditor
          v-model="target"
          :resources="resources"
          :identity="session.actor?.identity"
          :disabled="locked || !allowed || !!error"
          prefix="change"
        />
        <ul v-if="errors.length" class="feedback error" role="alert">
          <li v-for="e in errors" :key="e">{{ e }}</li>
        </ul>
        <div class="form-actions">
          <el-button
            type="primary"
            :loading="previewing"
            :disabled="locked || !allowed || !!error"
            data-testid="change-preview"
            @click="state.preview"
            >预览最终目标</el-button
          >
        </div>
      </section>
      <section class="panel">
        <h2>02 / 资源检查</h2>
        <ApiFeedback
          :error="previewError"
          :resources="resources"
          test-id="preview-error"
          :loading="previewing"
          @retry="state.preview"
        /><template v-if="result"
          ><p
            :class="['feedback', result.check.feasible ? 'success' : 'warning']"
          >
            {{
              result.check.feasible
                ? '当前预览可行。创建与最终确认仍会重新检查资源。'
                : '当前目标有冲突，请调整输入或选择候选方案。'
            }}
          </p>
          <ConflictList :conflicts="result.check.conflicts"
        /></template>
        <p v-else-if="!previewing" class="empty-state">
          填写目标后预览，查看精确的冲突资源与时段。
        </p>
      </section>
    </div>
    <section v-if="result" class="panel">
      <AlternativesPanel
        :alternatives="result.alternatives"
        :resources="resources"
        :disabled="locked || !allowed"
        @select="state.select"
      />
    </section>
    <section v-if="result" class="panel" data-testid="change-impact">
      <ImpactPanel
        :impact="result.impact"
        :current-risk="error ? undefined : activity.resourceRisk"
        :resources="resources"
      /><label for="change-reason">改期原因（必填，最多1000字）</label
      ><el-input
        id="change-reason"
        v-model="reason"
        type="textarea"
        :rows="3"
        maxlength="1000"
        :disabled="locked"
        data-testid="change-reason"
      />
      <p class="caption">
        创建后目标和影响快照不可直接修改。若需要换方案，请撤回后重新申请；处理中暂停报名和退出。
      </p>
      <div class="form-actions">
        <el-button
          type="primary"
          :disabled="!ready"
          data-testid="change-create"
          @click="state.create"
          >确认影响并创建改期</el-button
        >
      </div>
    </section>
    <el-button
      :disabled="loading || previewing || mutation.busy.value"
      @click="state.load()"
      >查询最新活动状态</el-button
    >
  </template>
</template>
