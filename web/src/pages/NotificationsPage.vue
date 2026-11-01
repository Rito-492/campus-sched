<script setup lang="ts">
import { onBeforeUnmount, ref, watch } from 'vue'
import { onBeforeRouteLeave } from 'vue-router'
import { api } from '../api'
import type { NotificationView } from '../types/api'
import { formatTime } from '../domain/format'
import ApiFeedback from '../components/ApiFeedback.vue'
import MutationFeedback from '../components/MutationFeedback.vue'
import { useMutation } from '../composables/useMutation'
const items = ref<NotificationView[]>([]),
  unread = ref(false),
  loading = ref(false),
  error = ref<unknown>(null),
  truncated = ref(false)
let generation = 0
const mutation = useMutation({
  confirmed: () => load(true),
  conflict: () => load(true),
})
async function load(rethrow = false) {
  const turn = ++generation
  loading.value = true
  error.value = null
  items.value = []
  try {
    const r = await api.notifications(unread.value)
    if (turn !== generation) return
    items.value = r.data
    truncated.value = !!r.truncated
  } catch (e) {
    if (turn === generation) error.value = e
    if (rethrow) throw e
  } finally {
    if (turn === generation) loading.value = false
  }
}
async function mark(id: string) {
  if (loading.value || mutation.locked.value) return
  await mutation.execute(api.readNotification(id))
}
watch(unread, () => load(), { immediate: true })
onBeforeUnmount(() => generation++)
onBeforeRouteLeave(
  (to) =>
    to.path === '/login' ||
    !mutation.uncertain.value ||
    window.confirm('标记已读结果尚未确认，确定离开？'),
)
</script>
<template>
  <section class="page-heading">
    <span class="eyebrow">协同工作 / 消息</span>
    <h1>通知中心</h1>
    <p class="muted">已读只表示看过消息，不代替人员确认或审批。</p>
  </section>
  <section class="panel">
    <div class="toolbar">
      <el-switch v-model="unread" active-text="只看未读" /><el-button
        :disabled="loading || mutation.busy.value"
        @click="load()"
        >刷新</el-button
      >
    </div>
    <ApiFeedback
      :error="error"
      :loading="loading"
      :truncated="truncated"
      @retry="load()"
    />
    <MutationFeedback :mutation="mutation" @refresh="load()" />
    <p v-if="!loading && !error && !items.length" class="empty-state">
      暂无通知。
    </p>
    <article
      v-for="item in items"
      :key="item.id"
      class="notification"
      data-testid="notification-item"
    >
      <span class="badge">{{ item.readAt ? '已读' : '未读' }}</span>
      <h2>{{ item.title }}</h2>
      <p>{{ item.body }}</p>
      <p class="muted">
        {{ formatTime(item.createdAt) }} · 版本 {{ item.versionNo ?? '—' }}
      </p>
      <div class="toolbar">
        <RouterLink
          v-if="item.activityId"
          :to="`/activities/${item.activityId}`"
          >查看活动</RouterLink
        ><el-button
          v-if="!item.readAt"
          :disabled="loading || mutation.locked.value"
          data-testid="notification-read"
          @click="mark(item.id)"
          >标为已读</el-button
        >
      </div>
    </article>
  </section>
</template>
