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
import StoreDetailView from '@/views/StoreDetailView.vue'
import LogisticsView from '@/views/LogisticsView.vue'
import LogisticsChannelDetailView from '@/views/LogisticsChannelDetailView.vue'
import WorkflowView from '@/views/WorkflowView.vue'
import FinanceView from '@/views/FinanceView.vue'
import NotFoundView from '@/views/NotFoundView.vue'
import ApplyView from '@/views/ApplyView.vue'
import ActivationView from '@/views/ActivationView.vue'
import PlatformOnboardingView from '@/views/PlatformOnboardingView.vue'
import GuestEstimateLeadsView from '@/views/GuestEstimateLeadsView.vue'
import WarehouseWorkView from '@/views/WarehouseWorkView.vue'
import TrackingView from '@/views/TrackingView.vue'
import { isPublicRouteName, publicRouteRedirect } from '@/navigation/access'

declare module 'vue-router' {
  interface RouteMeta { requiresAuth?: boolean; scope?: 'PLATFORM' | 'TENANT'; permission?: string; anyPermissions?: string[] }
}

const moduleRoutes: RouteRecordRaw[] = [
  { path: 'tenants', name: 'app-tenants', component: DirectoryView, props: { domain: 'tenants' }, meta: { scope: 'PLATFORM', permission: 'tenant:read' } },
  { path: 'onboarding-applications', name: 'app-onboarding-applications', component: PlatformOnboardingView, meta: { scope: 'PLATFORM', permission: 'tenant:manage' } },
  { path: 'guest-estimate-leads', name: 'app-guest-estimate-leads', component: GuestEstimateLeadsView, meta: { scope: 'PLATFORM', permission: 'tenant:manage' } },
  { path: 'users', name: 'app-users', component: DirectoryView, props: { domain: 'users' }, meta: { scope: 'TENANT', permission: 'user:manage' } },
  { path: 'stores', name: 'app-stores', component: DirectoryView, props: { domain: 'stores' }, meta: { scope: 'TENANT', permission: 'store:read' } },
  { path: 'stores/:storeId', name: 'app-store-detail', component: StoreDetailView, meta: { scope: 'TENANT', permission: 'store:read' } },
  { path: 'rbac', name: 'app-rbac', component: DirectoryView, props: { domain: 'rbac' }, meta: { scope: 'TENANT', permission: 'role:read' } },
  { path: 'logistics', name: 'app-logistics', component: LogisticsView, meta: { permission: 'logistics:read' } },
  { path: 'logistics/:channelId', name: 'app-logistics-detail', component: LogisticsChannelDetailView, meta: { scope: 'TENANT', permission: 'logistics:read' } },
  { path: 'quotes', name: 'app-quotes', component: WorkflowView, props: { domain: 'quotes' }, meta: { scope: 'TENANT', permission: 'quote:read' } },
  { path: 'quotes/create', name: 'app-quote-create', component: QuoteView, meta: { scope: 'TENANT', permission: 'quote:create' } },
  { path: 'orders', name: 'app-orders', component: WorkflowView, props: { domain: 'orders' }, meta: { scope: 'TENANT', permission: 'order:read' } },
  { path: 'warehouse', name: 'app-warehouse', component: WarehouseWorkView, meta: { scope: 'TENANT', permission: 'warehouse:manage' } },
  { path: 'tracking', name: 'app-tracking', component: TrackingView, meta: { scope: 'TENANT', permission: 'tracking:read' } },
  { path: 'exceptions', name: 'app-exceptions', component: WorkflowView, props: { domain: 'exceptions' }, meta: { scope: 'TENANT', permission: 'exception:read' } },
  { path: 'billing', name: 'app-billing', component: FinanceView, meta: { scope: 'TENANT', anyPermissions: ['billing:read', 'finance:bill-import', 'finance:reconcile'] } },
  { path: 'audit', name: 'app-audit', component: WorkflowView, props: { domain: 'audit' }, meta: { permission: 'audit:read' } },
]

const router = createRouter({
  history: createWebHistory(),
  scrollBehavior(to, _from, savedPosition) {
    if (savedPosition) return savedPosition
    const navigation = performance.getEntriesByType('navigation')[0] as PerformanceNavigationTiming | undefined
    if (to.hash && navigation?.type === 'reload') return { top: 0 }
    if (to.hash) return { el: to.hash, top: 84, behavior: 'smooth' }
    return { top: 0 }
  },
  routes: [
    { path: '/', component: PublicLayout, children: [{ path: '', name: 'home', component: HomeView }, { path: 'quote', name: 'quote', component: QuoteView }, { path: 'apply', name: 'apply', component: ApplyView }, { path: 'activate', name: 'activate', component: ActivationView }] },
    { path: '/login', name: 'login', component: LoginView }, { path: '/403', name: 'forbidden', component: ForbiddenView },
    { path: '/app', component: AppLayout, meta: { requiresAuth: true }, children: [{ path: '', name: 'dashboard', component: DashboardView }, ...moduleRoutes] },
    { path: '/:pathMatch(.*)*', name: 'not-found', component: NotFoundView },
  ],
})

router.beforeEach(async to => {
  const auth = useAuthStore()
  // All protected route decisions wait for the single session-restore promise. This avoids
  // treating the short period between receiving a token and loading /users/me as a 403.
  if ((to.meta.requiresAuth || isPublicRouteName(to.name)) && !auth.initialized) await auth.restoreSession()
  const publicRedirect = publicRouteRedirect(auth, to.name)
  if (publicRedirect) return publicRedirect
  if (to.meta.requiresAuth && !auth.isAuthenticated) return { name: 'login', query: { redirect: to.fullPath } }
  if (to.meta.requiresAuth && !auth.initialized) return false
  if (to.meta.scope && !auth.hasScope(to.meta.scope)) return { name: 'forbidden' }
  if (to.meta.permission && !auth.hasPermission(to.meta.permission)) return { name: 'forbidden' }
  if (to.meta.anyPermissions && !to.meta.anyPermissions.some(auth.hasPermission)) return { name: 'forbidden' }
})
if (typeof window !== 'undefined') {
  window.addEventListener('shipflow:session-expired', () => {
    if (router.currentRoute.value.name !== 'login') {
      void router.replace({ name: 'login', query: { redirect: router.currentRoute.value.fullPath } })
    }
  })
}
export default router
