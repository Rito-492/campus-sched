<script setup lang="ts">
import { onMounted } from 'vue'
import { onBeforeRouteLeave, useRouter } from 'vue-router'
import UsersPanel from '../components/UsersPanel.vue'
import { useUserAdmin } from '../composables/useUserAdmin'
import { useSession } from '../stores/session'
import { hasAccountAccess } from '../domain/authorization'
const session = useSession(),
  router = useRouter()
const admin = useUserAdmin(
  () => session.actor,
  async () => {
    await session.restore()
    if (!hasAccountAccess(session.actor)) await router.replace('/forbidden')
  },
)
const {
  users,
  organizations,
  resources,
  loading,
  busy,
  ready,
  truncated,
  loadError,
  actionError,
  message,
  completed,
  uncertain,
} = admin
onMounted(admin.load)
onBeforeRouteLeave(
  (to) =>
    !uncertain.value ||
    to.path === '/login' ||
    window.confirm(
      '账号变更结果尚未确认。离开后请先查询最新状态，避免重复提交。确定离开？',
    ),
)
</script>
<template>
  <UsersPanel
    :users="users"
    :organizations="organizations"
    :resources="resources"
    :current-user-id="session.actor?.id ?? ''"
    :ready="ready"
    :loading="loading"
    :busy="busy"
    :uncertain="uncertain"
    :truncated="truncated"
    :load-error="loadError"
    :action-error="actionError"
    :message="message"
    :completed="completed"
    @reload="admin.load"
    @submit="admin.submit"
    @retry="admin.submit()"
  />
</template>
