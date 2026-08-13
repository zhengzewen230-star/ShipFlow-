import { createRouter, createWebHistory, type RouteRecordRaw } from 'vue-router'
import { useAuthStore } from '@/stores/auth'
import PublicLayout from '@/layouts/PublicLayout.vue'
import AppLayout from '@/layouts/AppLayout.vue'
import HomeView from '@/views/HomeView.vue'
import QuoteView from '@/views/QuoteView.vue'
import LoginView from '@/views/LoginView.vue'
import DashboardView from '@/views/DashboardView.vue'
import ForbiddenView from '@/views/ForbiddenView.vue'
import DirectoryView from '@/views/DirectoryView.vue'
import LogisticsView from '@/views/LogisticsView.vue'
import WorkflowView from '@/views/WorkflowView.vue'
import NotFoundView from '@/views/NotFoundView.vue'
import ApplyView from '@/views/ApplyView.vue'

declare module 'vue-router' {
  interface RouteMeta { requiresAuth?: boolean; scope?: 'PLATFORM' | 'TENANT'; permission?: string; anyPermissions?: string[] }
}

const moduleRoutes: RouteRecordRaw[] = [
  { path: 'tenants', name: 'app-tenants', component: DirectoryView, props: { domain: 'tenants' }, meta: { scope: 'PLATFORM', permission: 'tenant:read' } },
  { path: 'users', name: 'app-users', component: DirectoryView, props: { domain: 'users' }, meta: { scope: 'TENANT', permission: 'user:manage' } },
  { path: 'stores', name: 'app-stores', component: DirectoryView, props: { domain: 'stores' }, meta: { scope: 'TENANT', permission: 'store:read' } },
  { path: 'rbac', name: 'app-rbac', component: DirectoryView, props: { domain: 'rbac' }, meta: { scope: 'TENANT', permission: 'role:read' } },
  { path: 'logistics', name: 'app-logistics', component: LogisticsView, meta: { permission: 'logistics:read' } },
  { path: 'quotes', name: 'app-quotes', component: WorkflowView, props: { domain: 'quotes' }, meta: { scope: 'TENANT' } },
  { path: 'orders', name: 'app-orders', component: WorkflowView, props: { domain: 'orders' }, meta: { scope: 'TENANT' } },
  { path: 'warehouse', name: 'app-warehouse', component: WorkflowView, props: { domain: 'warehouse' }, meta: { scope: 'TENANT', permission: 'warehouse:manage' } },
  { path: 'tracking', name: 'app-tracking', component: WorkflowView, props: { domain: 'tracking' }, meta: { scope: 'TENANT' } },
  { path: 'exceptions', name: 'app-exceptions', component: WorkflowView, props: { domain: 'exceptions' }, meta: { scope: 'TENANT' } },
  { path: 'billing', name: 'app-billing', component: WorkflowView, props: { domain: 'billing' }, meta: { scope: 'TENANT', anyPermissions: ['finance:bill-import', 'finance:reconcile'] } },
  { path: 'audit', name: 'app-audit', component: WorkflowView, props: { domain: 'audit' }, meta: { permission: 'audit:read' } },
]

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    if (to.hash) return { el: to.hash, top: 84, behavior: 'smooth' }
    return { top: 0 }
  },
  routes: [
    { path: '/', component: PublicLayout, children: [{ path: '', name: 'home', component: HomeView }, { path: 'quote', name: 'quote', component: QuoteView }, { path: 'apply', name: 'apply', component: ApplyView }] },
    { path: '/login', name: 'login', component: LoginView }, { path: '/403', name: 'forbidden', component: ForbiddenView },
    { path: '/app', component: AppLayout, meta: { requiresAuth: true }, children: [{ path: '', name: 'dashboard', component: DashboardView, meta: { scope: 'TENANT' } }, ...moduleRoutes] },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView },
  ],
})

router.beforeEach(async to => {
  const auth = useAuthStore()
  if ((to.meta.requiresAuth || to.name === 'login') && !auth.initialized) await auth.restoreSession()
  if (to.meta.requiresAuth && !auth.isAuthenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.scope && !auth.hasScope(to.meta.scope)) return { name: 'forbidden' }
  if (to.meta.permission && !auth.hasPermission(to.meta.permission)) return { name: 'forbidden' }
  if (to.meta.anyPermissions && !to.meta.anyPermissions.some(auth.hasPermission)) return { name: 'forbidden' }
  if (to.name === 'login' && auth.isAuthenticated) return { name: auth.scope === 'PLATFORM' ? 'app-tenants' : 'dashboard' }
})
export default router
