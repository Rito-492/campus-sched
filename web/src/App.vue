<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { useSession } from './stores/session'
import { navigation } from './domain/access'
import ApiFeedback from './components/ApiFeedback.vue'
const route = useRoute()
const session = useSession()
const menuOpen = ref(false)
const error = ref<unknown>(null)
const menu = computed(() =>
  navigation.filter((item) => item.visible(session.actor)),
)
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
  <div v-else class="app-shell">
    <aside class="sidebar" :class="{ open: menuOpen }">
      <RouterLink class="brand" to="/activities"
        ><span class="brand-mark">校</span
        ><span>校园活动<small>协同与资源调度</small></span></RouterLink
      >
      <nav aria-label="主导航">
        <template v-for="(item, index) in menu" :key="item.path">
          <p
            v-if="index === 0 || menu[index - 1]?.section !== item.section"
            class="nav-section"
          >
            {{ item.section }}
          </p>
          <RouterLink :to="item.path" @click="menuOpen = false">{{
            item.label
          }}</RouterLink>
        </template>
      </nav>
      <p class="sidebar-note">让每一次校园活动<br />安排有序，协作有据。</p>
    </aside>
    <div class="workspace">
      <header class="topbar">
        <el-button
          class="menu-toggle"
          :aria-expanded="menuOpen"
          aria-label="展开或收起导航"
          @click="menuOpen = !menuOpen"
          >菜单</el-button
        ><span>校园活动协同平台</span>
        <div class="account">
          <span
            >{{ session.actor?.displayName }}
            <small>{{
              session.actor?.identity === 'TEACHER' ? '教师' : '学生'
            }}</small></span
          ><el-button text @click="logout">退出</el-button>
        </div>
      </header>
      <main id="main" tabindex="-1">
        <ApiFeedback :error="error" @retry="logout" /><RouterView
          :key="route.fullPath"
        />
      </main>
    </div>
  </div>
</template>
