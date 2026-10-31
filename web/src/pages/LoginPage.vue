<script setup lang="ts">
import { ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { useSession } from '../stores/session'
import ApiFeedback from '../components/ApiFeedback.vue'
const username = ref(''),
  password = ref(''),
  busy = ref(false),
  error = ref<unknown>(null)
const session = useSession(),
  router = useRouter(),
  route = useRoute()
async function submit() {
  if (busy.value || !username.value || !password.value) return
  busy.value = true
  error.value = null
  try {
    await session.login(username.value.trim(), password.value)
    password.value = ''
    const target =
      typeof route.query.redirect === 'string'
        ? route.query.redirect
        : '/activities'
    await router.replace(
      target.startsWith('/') && !target.startsWith('//') && target !== '/login'
        ? target
        : '/activities',
    )
  } catch (e) {
    error.value = e
  } finally {
    busy.value = false
  }
}
</script>
<template>
  <main id="main" class="login-page">
    <section class="login-intro">
      <span class="eyebrow">CAMPUS · TOGETHER</span>
      <h1>校园里的好想法，<br />从一次有序的活动开始。</h1>
      <p>从活动申请、资源安排，到报名与变更，<br />让每位参与者知道下一步。</p>
      <div class="intro-lines">
        <span>01　发起活动</span><span>02　协同确认</span
        ><span>03　共同参与</span>
      </div>
    </section>
    <section class="panel login-card">
      <span class="brand-mark">校</span>
      <h2>欢迎回来</h2>
      <p class="muted">登录校园活动协同与资源调度系统</p>
      <p v-if="route.query.expired" class="feedback warning">
        登录已失效，请重新登录。
      </p>
      <form @submit.prevent="submit">
        <label for="username">账号</label
        ><el-input
          id="username"
          v-model="username"
          autocomplete="username"
          data-testid="login-username"
          placeholder="输入账号"
          :disabled="busy"
        /><label for="password">密码</label
        ><el-input
          id="password"
          v-model="password"
          type="password"
          autocomplete="current-password"
          show-password
          data-testid="login-password"
          placeholder="输入密码"
          :disabled="busy"
        /><el-button
          type="primary"
          native-type="submit"
          class="full-width"
          :loading="busy"
          :disabled="!username || !password"
          data-testid="login-submit"
          >登录工作台</el-button
        >
      </form>
      <ApiFeedback :error="error" test-id="login-error" @retry="submit" />
      <p class="caption">账号身份与审批权限独立。需要账号时，请联系管理员。</p>
    </section>
  </main>
</template>
