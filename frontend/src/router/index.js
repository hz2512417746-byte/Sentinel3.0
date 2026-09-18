import { createRouter, createWebHistory } from 'vue-router'
import {
  LayoutDashboard, ScrollText, MessageSquareText, Users, UserRound,
  ShieldBan, UserX, BellRing, Settings2, SlidersHorizontal, Workflow,
} from '@lucide/vue'

// 各页面的懒加载 + 元信息（title 用于侧边栏菜单与文档标题，icon 为 lucide 组件）
const routes = [
  { path: '/',         name: 'home',     component: () => import('../views/Home.vue'),     meta: { title: '总览',   icon: LayoutDashboard } },
  { path: '/logs',     name: 'logs',     component: () => import('../views/Logs.vue'),     meta: { title: '日志',   icon: ScrollText } },
  { path: '/text',     name: 'text',     component: () => import('../views/TextDetect.vue'), meta: { title: '文本识别', icon: MessageSquareText } },
  { path: '/users',    name: 'users',    component: () => import('../views/Users.vue'),    meta: { title: '用户',   icon: Users } },
  { path: '/profiles', name: 'profiles', component: () => import('../views/UserProfiles.vue'), meta: { title: '画像', icon: UserRound } },
  { path: '/history',  name: 'history',  component: () => import('../views/History.vue'),  meta: { title: '拦截',   icon: ShieldBan } },
  { path: '/banned',   name: 'banned',   component: () => import('../views/Banned.vue'),   meta: { title: '封号',   icon: UserX } },
  { path: '/alerts',   name: 'alerts',   component: () => import('../views/Alerts.vue'),   meta: { title: '告警',   icon: BellRing } },
  { path: '/rules',    name: 'rules',    component: () => import('../views/Rules.vue'),    meta: { title: '规则',   icon: Settings2 } },
  { path: '/tuning',   name: 'tuning',   component: () => import('../views/Tuning.vue'),   meta: { title: '调参',   icon: SlidersHorizontal } },
  { path: '/pipeline', name: 'pipeline', component: () => import('../views/Pipeline.vue'), meta: { title: '管线',   icon: Workflow } },
  { path: '/:pathMatch(.*)*', redirect: '/' },
]

const router = createRouter({
  history: createWebHistory(),
  routes,
})

// 页面切换时同步文档标题
router.afterEach((to) => {
  const base = 'Sentinel 3.0 · DPI 实时反诈预警系统'
  document.title = to.meta?.title ? `${to.meta.title} · ${base}` : base
})

export default router
