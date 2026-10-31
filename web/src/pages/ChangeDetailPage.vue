<script setup lang="ts">
import { onMounted, ref, watch } from 'vue'
import { onBeforeRouteLeave, useRoute } from 'vue-router'
import { api } from '../api'
import { ApiError } from '../api/client'
import { useSession } from '../stores/session'
import { useMutation } from '../composables/useMutation'
import { changeActions } from '../domain/change'
import { shanghaiLocal } from '../domain/format'
import type {
  ChangeView,
  ActivityView,
  ResourceView,
  ChangeDecision,
  CalendarEntry,
  NotificationView,
} from '../types/api'
import ApiFeedback from '../components/ApiFeedback.vue'
import MutationFeedback from '../components/MutationFeedback.vue'
import ImpactPanel from '../components/ImpactPanel.vue'
import ChangeTimeline from '../components/ChangeTimeline.vue'
import ChangeActions from '../components/ChangeActions.vue'
import ConflictList from '../components/ConflictList.vue'
import PlanSummary from '../components/PlanSummary.vue'
import ResourceCalendar from '../components/ResourceCalendar.vue'
const route = useRoute(),
  session = useSession(),
  item = ref<ChangeView | null>(null),
  activity = ref<ActivityView | null>(null),
  resources = ref<ResourceView[]>([])
const loading = ref(false),
  error = ref<unknown>(null),
  truncated = ref(false),
  relatedError = ref<unknown>(null),
  entries = ref<CalendarEntry[]>([]),
  notices = ref<NotificationView[]>([]),
  calendarTruncated = ref(false),
  noticesTruncated = ref(false),
  relatedReady = ref(false),
  calendarDate = ref('')
const mutation = useMutation<ChangeView>({
  confirmed: async (data) => {
    item.value = data
    await load(true)
  },
  conflict: () => load(true),
})
async function load(rethrow = false) {
  loading.value = true
  error.value = null
  relatedError.value = null
  relatedReady.value = false
  try {
    await session.restore()
    const c = await api.change(String(route.params.id))
    item.value = c.data
    const [a, r] = await Promise.all([
      api.activity(c.data.activityId),
      api.resources(),
    ])
    activity.value = a.data
    resources.value = r.data
    truncated.value = !!r.truncated
    if (c.data.status === 'APPLIED') {
      calendarDate.value = shanghaiLocal(a.data.input.plan.startAt).slice(0, 10)
      const from = `${calendarDate.value}T00:00:00+08:00`,
        to = new Date(Date.parse(from) + 86400000).toISOString()
      try {
        const [calendar, notifications] = await Promise.all([
          api.calendar(from, to),
          api.notifications(),
        ])
        entries.value = calendar.data
        calendarTruncated.value = !!calendar.truncated
        notices.value = notifications.data.filter(
          (n) => n.activityId === a.data.id,
        )
        noticesTruncated.value = !!notifications.truncated
        relatedReady.value = true
      } catch (e) {
        relatedError.value = e
      }
    }
  } catch (e) {
    error.value = e
    if (e instanceof ApiError && [401, 403, 404].includes(e.status)) {
      item.value = null
      activity.value = null
    }
    if (rethrow) throw e
  } finally {
    loading.value = false
  }
}
function decide(kind: 'confirm' | 'approve' | 'reserve', data: ChangeDecision) {
  if (
    !item.value ||
    !activity.value ||
    loading.value ||
    error.value ||
    mutation.locked.value ||
    !changeActions(session.actor, activity.value, item.value, resources.value)[
      kind
    ]
  )
    return
  if (
    data.comment.length > 1000 ||
    (data.decision === 'REJECT' && !data.comment.trim())
  )
    return
  const commands = {
    confirm: api.confirmChange,
    approve: api.approveChange,
    reserve: api.reserveChange,
  }
  void mutation.execute(commands[kind](item.value.id, data))
}
function withdraw(reason: string) {
  if (
    !item.value ||
    !activity.value ||
    loading.value ||
    error.value ||
    mutation.locked.value ||
    !changeActions(session.actor, activity.value, item.value, resources.value)
      .withdraw ||
    !reason.trim() ||
    reason.length > 1000
  )
    return
  void mutation.execute(api.withdrawChange(item.value.id, reason))
}
watch(mutation.error, (e) => {
  if (
    e instanceof ApiError &&
    e.status === 409 &&
    e.details &&
    typeof e.details === 'object' &&
    'change' in e.details
  ) {
    const updated = e.details.change as ChangeView
    if (updated?.id === item.value?.id && updated.status === 'CONFLICTED')
      item.value = updated
  }
})
onMounted(() => load())
onBeforeRouteLeave(
  (to) =>
    to.path === '/login' ||
    !mutation.uncertain.value ||
    window.confirm('改期处理结果尚未确认。离开后请查询进度，确定离开？'),
)
</script>
<template>
  <section class="page-heading">
    <RouterLink v-if="item" :to="`/activities/${item.activityId}`"
      >← 活动详情与历史</RouterLink
    >
    <h1>改期进度</h1>
    <p class="muted">
      {{ activity?.input.title }} · 目标在全部确认完成前不会替换当前有效安排。
    </p>
  </section>
  <div class="toolbar">
    <el-button
      :disabled="loading || mutation.busy.value"
      data-testid="change-refresh"
      @click="load()"
      >刷新进度</el-button
    ><RouterLink to="/notifications">通知中心</RouterLink
    ><RouterLink to="/resources/calendar">资源日历</RouterLink>
  </div>
  <ApiFeedback
    :error="error"
    :loading="loading"
    :truncated="truncated"
    @retry="load()"
  />
  <MutationFeedback
    :mutation="mutation"
    :resources="resources"
    @refresh="load()"
  />
  <template v-if="item">
    <section v-if="activity" class="panel">
      <h2>服务端当前有效安排</h2>
      <p data-testid="current-version">
        有效版本 {{ activity.effectiveVersionNo ?? '尚未生效' }}
      </p>
      <p
        v-if="activity.resourceRisk === 'AT_RISK'"
        class="feedback warning"
        data-testid="risk-banner"
      >
        当前资源存在故障风险。失败、拒绝或撤回本次改期不会解除该风险。
      </p>
      <PlanSummary :plan="activity.input.plan" :resources="resources" />
    </section>
    <section class="panel" data-testid="change-impact">
      <ImpactPanel
        :impact="item.impact"
        :current-risk="error ? undefined : activity?.resourceRisk"
        :resources="resources"
        historical
      />
    </section>
    <section class="panel">
      <ChangeTimeline :change="item" /><ConflictList
        v-if="item.lastConflicts.length"
        :conflicts="item.lastConflicts"
      />
    </section>
    <ChangeActions
      v-if="activity"
      :actor="session.actor"
      :activity="activity"
      :change="item"
      :resources="resources"
      :disabled="loading || mutation.locked.value || !!error"
      @decision="decide"
      @withdraw="withdraw"
    />
    <section v-if="item.status === 'APPLIED'" class="panel">
      <h2>生效后的日历与通知</h2>
      <ApiFeedback
        :error="relatedError"
        :truncated="calendarTruncated || noticesTruncated"
        @retry="load()"
      /><template v-if="relatedReady"
        ><ResourceCalendar
          :date="calendarDate"
          :resources="
            resources.filter(
              (r) =>
                r.id === activity?.input.plan.venueId ||
                activity?.input.plan.equipment.some(
                  (e) => e.resourceId === r.id,
                ),
            )
          "
          :entries="
            entries.filter((e) =>
              resources.some(
                (r) =>
                  r.id === e.resourceId &&
                  (r.id === activity?.input.plan.venueId ||
                    activity?.input.plan.equipment.some(
                      (n) => n.resourceId === r.id,
                    )),
              ),
            )
          "
          :truncated="calendarTruncated"
        />
        <h3>本活动的通知</h3>
        <p v-if="!notices.length" class="muted">
          当前查询尚无相关通知。通知可能仍在投递，未收到通知不表示改期失败。
        </p>
        <article v-for="n in notices" :key="n.id" class="notification">
          <strong>{{ n.title }}</strong>
          <p>{{ n.body }}</p>
          <p class="caption">
            版本 {{ n.versionNo ?? '—' }} ·
            {{ n.readAt ? '已读' : '未读' }}，已读不代替确认。
          </p>
        </article></template
      >
    </section>
  </template>
</template>
