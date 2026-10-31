<script setup lang="ts">
import { onMounted, ref } from 'vue'
import { api } from '../api'
import type { NotificationView } from '../types/api'
import { formatTime } from '../domain/format'
import ApiFeedback from '../components/ApiFeedback.vue'
const items = ref<NotificationView[]>([]),
  unread = ref(false),
  loading = ref(false),
  error = ref<unknown>(null),
  truncated = ref(false)
let pending: ReturnType<typeof api.readNotification> | null = null
async function load() {
  loading.value = true
  error.value = null
  try {
    const r = await api.notifications(unread.value)
    items.value = r.data
    truncated.value = !!r.truncated
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}
async function mark(id?: string) {
  if (loading.value) return
  if (id) pending = api.readNotification(id)
  if (!pending) return
  loading.value = true
  error.value = null
  try {
    await pending.run()
    pending = null
    await load()
  } catch (e) {
    error.value = e
  } finally {
    loading.value = false
  }
}
onMounted(load)
</script>
<template>
  <section class="page-heading">
    <span class="eyebrow">协同工作 / 消息</span>
    <h1>通知中心</h1>
    <p class="muted">已读只表示看过消息，不代替人员确认或审批。</p>
  </section>
  <section class="panel">
    <div class="toolbar">
      <el-switch
        v-model="unread"
        active-text="只看未读"
        @change="load"
      /><el-button :disabled="loading" @click="load">刷新</el-button>
    </div>
    <ApiFeedback
      :error="error"
      :loading="loading"
      :truncated="truncated"
      @retry="pending ? mark() : load()"
    />
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
          :disabled="loading"
          @click="mark(item.id)"
          >标为已读</el-button
        >
      </div>
    </article>
  </section>
</template>
