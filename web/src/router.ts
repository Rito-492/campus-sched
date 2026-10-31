import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useSession } from './stores/session'
import { readToken, ApiError } from './api/client'
import { canReview, hasPermission, isApplicant } from './domain/access'
import type { Actor } from './types/api'
export const productRoutes: RouteRecordRaw[] = [
  {
    path: '/login',
    component: () => import('./pages/LoginPage.vue'),
    meta: { title: '登录', public: true },
  },
  {
    path: '/activities',
    component: () => import('./pages/ActivitiesPage.vue'),
    meta: { title: '活动工作台' },
  },
  {
    path: '/activities/new',
    component: () => import('./pages/ActivityCreatePage.vue'),
    meta: { title: '创建活动', allow: isApplicant },
  },
  {
    path: '/activities/:id',
    component: () => import('./pages/ActivityDetailPage.vue'),
    meta: { title: '活动详情' },
  },
  {
    path: '/resources/calendar',
    component: () => import('./pages/CalendarPage.vue'),
    meta: { title: '资源日历' },
  },
  {
    path: '/resources/manage',
    component: () => import('./pages/PendingPage.vue'),
    meta: {
      title: '资源管理',
      dependency: '资源建档、故障登记及申请资格编辑正在接入。',
      allow: (a: Actor | null) => hasPermission(a, 'RESOURCE_MANAGE'),
    },
  },
  {
    path: '/activities/:id/change',
    component: () => import('./pages/PendingPage.vue'),
    meta: {
      title: '申请改期',
      dependency: '改期预览、备选方案与影响清单正在接入。',
      allow: isApplicant,
    },
  },
  {
    path: '/changes/:id',
    component: () => import('./pages/PendingPage.vue'),
    meta: {
      title: '改期进度',
      dependency: '人员确认、复审与资源确认正在接入。',
    },
  },
  {
    path: '/reviews',
    component: () => import('./pages/ActivitiesPage.vue'),
    meta: { title: '审批与待办', allow: canReview },
  },
  {
    path: '/notifications',
    component: () => import('./pages/NotificationsPage.vue'),
    meta: { title: '通知中心' },
  },
  {
    path: '/admin/users',
    component: () => import('./pages/PendingPage.vue'),
    meta: {
      title: '账号与授权',
      dependency: '账号创建、启停和授权范围编辑正在接入。',
      allow: (a: Actor | null) => hasPermission(a, 'USER_MANAGE'),
    },
  },
]
export const router = createRouter({
  history: createWebHistory(),
  routes: [
    { path: '/', redirect: '/activities' },
    ...productRoutes,
    {
      path: '/unavailable',
      component: () => import('./pages/PendingPage.vue'),
      meta: {
        title: '连接未恢复',
        public: true,
        dependency: '暂时无法验证登录状态。请检查服务连接后重试。',
      },
    },
    {
      path: '/forbidden',
      component: () => import('./pages/PendingPage.vue'),
      meta: {
        title: '无访问权限',
        dependency: '当前账号没有此页面的操作权限。',
      },
    },
    {
      path: '/:pathMatch(.*)*',
      component: () => import('./pages/PendingPage.vue'),
      meta: {
        title: '页面不存在',
        public: true,
        dependency: '请检查地址，或返回活动工作台。',
      },
    },
  ],
  scrollBehavior: () => ({ top: 0 }),
})
if (import.meta.env.DEV)
  router.addRoute({
    path: '/__prototype',
    component: () => import('./dev/PrototypePage.vue'),
    meta: { title: '组件开发原型', public: true },
  })
router.beforeEach(async (to) => {
  if (to.meta.public) return true
  if (!readToken()) return { path: '/login', query: { redirect: to.fullPath } }
  const session = useSession()
  try {
    await session.restore()
  } catch (error) {
    return error instanceof ApiError && error.status === 401
      ? { path: '/login', query: { redirect: to.fullPath } }
      : '/unavailable'
  }
  const allow = to.meta.allow as ((actor: Actor | null) => boolean) | undefined
  if (allow && !allow(session.actor)) return '/forbidden'
})
router.afterEach((to) => {
  document.title = `${String(to.meta.title ?? '校园活动')} · 校园活动协同`
})
window.addEventListener('session-expired', () => {
  if (!router.currentRoute.value.meta.public)
    void router.replace({
      path: '/login',
      query: { redirect: router.currentRoute.value.fullPath, expired: '1' },
    })
})
