<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRoute } from 'vue-router'
import { navigation } from '../domain/access'
import type { Actor } from '../types/api'

const props = withDefaults(
  defineProps<{
    actor: Actor | null
    activePath?: string
    mapLink?: (path: string) => string
    accountAction?: string
  }>(),
  { accountAction: '退出' },
)
defineEmits<{ accountAction: [] }>()
const route = useRoute()
const menuOpen = ref(false)
const menu = computed(() =>
  navigation.filter((item) => item.visible(props.actor)),
)
const link = (path: string) => (props.mapLink ? props.mapLink(path) : path)
const active = (path: string) =>
  (props.activePath ?? route.path).startsWith(path)
</script>

<template>
  <div class="app-shell">
    <aside class="sidebar" :class="{ open: menuOpen }">
      <RouterLink class="brand" :to="link('/activities')">
        <span class="brand-mark">校</span
        ><span>校园活动<small>协同与资源调度</small></span>
      </RouterLink>
      <nav aria-label="主导航">
        <template v-for="(item, index) in menu" :key="item.path">
          <p
            v-if="index === 0 || menu[index - 1]?.section !== item.section"
            class="nav-section"
          >
            {{ item.section }}
          </p>
          <RouterLink
            :to="link(item.path)"
            :class="{ 'menu-current': active(item.path) }"
            :aria-current="active(item.path) ? 'page' : undefined"
            @click="menuOpen = false"
            >{{ item.label }}</RouterLink
          >
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
        >
        <span>校园活动协同平台</span>
        <div class="account">
          <span
            >{{ actor?.displayName }}
            <small>{{
              actor?.identity === 'TEACHER' ? '教师' : '学生'
            }}</small></span
          >
          <el-button text @click="$emit('accountAction')">{{
            accountAction
          }}</el-button>
        </div>
      </header>
      <main id="main" tabindex="-1"><slot /></main>
    </div>
  </div>
</template>
