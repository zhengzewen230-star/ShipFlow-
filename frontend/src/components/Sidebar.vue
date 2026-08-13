<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  BarChart3, Building2, Calculator, ClipboardList, Warehouse, MapPinned,
  CircleAlert, ReceiptText, Database, ScrollText, LogOut, ShieldCheck, Store, Users,
} from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import BrandMark from './BrandMark.vue'

const router = useRouter()
const auth = useAuthStore()
const items = [
  { label: '概览', path: '/app', icon: BarChart3, scope: 'TENANT' },
  { label: '租户管理', path: '/app/tenants', icon: Building2, scope: 'PLATFORM', permission: 'tenant:read' },
  { label: '用户管理', path: '/app/users', icon: Users, scope: 'TENANT', permission: 'user:manage' },
  { label: '店铺管理', path: '/app/stores', icon: Store, scope: 'TENANT', permission: 'store:read' },
  { label: '角色与权限', path: '/app/rbac', icon: ShieldCheck, scope: 'TENANT', permission: 'role:read' },
  { label: '物流基础资料', path: '/app/logistics', icon: Database, permission: 'logistics:read' },
  { label: '报价', path: '/app/quotes', icon: Calculator, scope: 'TENANT' },
  { label: '订单', path: '/app/orders', icon: ClipboardList, scope: 'TENANT' },
  { label: '仓库', path: '/app/warehouse', icon: Warehouse, scope: 'TENANT', permission: 'warehouse:manage' },
  { label: '轨迹', path: '/app/tracking', icon: MapPinned, scope: 'TENANT' },
  { label: '异常与索赔', path: '/app/exceptions', icon: CircleAlert, scope: 'TENANT' },
  { label: '账单与对账', path: '/app/billing', icon: ReceiptText, scope: 'TENANT', anyPermission: ['finance:bill-import', 'finance:reconcile'] },
  { label: '审计日志', path: '/app/audit', icon: ScrollText, permission: 'audit:read' },
] as const

const visibleItems = computed(() => items.filter(item => {
  if ('scope' in item && item.scope && !auth.hasScope(item.scope)) return false
  if ('permission' in item && item.permission && !auth.hasPermission(item.permission)) return false
  if ('anyPermission' in item && item.anyPermission && !item.anyPermission.some(auth.hasPermission)) return false
  return true
}))

async function signOut() {
  await auth.logout()
  await router.replace('/login')
}
</script>

<template>
  <aside class="sidebar">
    <div class="sidebar__brand"><BrandMark /></div>
    <nav aria-label="控制台导航">
      <RouterLink v-for="item in visibleItems" :key="item.path" :to="item.path" :class="{ exact: item.path === '/app' }">
        <component :is="item.icon" :size="19" /><span>{{ item.label }}</span>
      </RouterLink>
    </nav>
    <div class="sidebar__footer">
      <div class="sidebar__user"><span>SF</span><div><b>{{ auth.currentUser?.displayName || auth.currentUser?.username }}</b><small>{{ auth.scope === 'PLATFORM' ? '平台账号' : '租户账号' }}</small></div></div>
      <button type="button" :disabled="auth.loading" @click="signOut"><LogOut :size="17" /> {{ auth.loading ? '退出中…' : '安全退出' }}</button>
    </div>
  </aside>
</template>
