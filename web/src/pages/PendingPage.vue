<script setup lang="ts">
import { useRoute } from 'vue-router'
const route = useRoute()
</script>
<template>
  <section class="page-heading">
    <span class="eyebrow">校园活动</span>
    <h1>{{ route.meta.title }}</h1>
  </section>
  <section class="panel empty-state" data-testid="page-pending">
    <h2>{{ route.meta.dependency }}</h2>
    <p class="muted">当前页面不提供已完成的业务结果。</p>
    <RouterLink to="/activities">返回活动工作台</RouterLink
    ><RouterLink v-if="route.path === '/unavailable'" to="/login"
      >重新登录</RouterLink
    >
  </section>
</template>
