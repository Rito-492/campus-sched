<script setup lang="ts">
import { ref } from 'vue'
import { useRoute } from 'vue-router'
import { useSession } from './stores/session'
import ApiFeedback from './components/ApiFeedback.vue'
import WorkspaceLayout from './components/WorkspaceLayout.vue'
const route = useRoute()
const session = useSession()
const error = ref<unknown>(null)
async function logout() {
  try {
    await session.logout()
  } catch (e) {
    error.value = e
  }
}
</script>
<template>
  <a class="skip-link" href="#main">跳到主要内容</a>
  <div v-if="route.meta.public" class="public-shell"><RouterView /></div>
  <WorkspaceLayout v-else :actor="session.actor" @account-action="logout">
    <ApiFeedback :error="error" @retry="logout" />
    <RouterView :key="route.fullPath" />
  </WorkspaceLayout>
</template>
