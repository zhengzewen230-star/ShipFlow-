<script setup lang="ts">
import { computed } from 'vue'
import { useRouter } from 'vue-router'
import {
  BarChart3, Building2, Calculator, ClipboardList, Warehouse, MapPinned,
  CircleAlert, ReceiptText, Database, ScrollText, LogOut, ShieldCheck, Store, Users,
} from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import { roleSummary, visibleConsoleItems, type ConsoleIcon } from '@/navigation/console'
import BrandMark from './BrandMark.vue'

const router = useRouter()
const auth = useAuthStore()
const icons: Record<ConsoleIcon, typeof BarChart3> = { overview: BarChart3, tenant: Building2, users: Users, quote: Calculator, store: Store, rbac: ShieldCheck, logistics: Database, order: ClipboardList, warehouse: Warehouse, tracking: MapPinned, exception: CircleAlert, billing: ReceiptText, audit: ScrollText }
const visibleItems = computed(() => visibleConsoleItems(auth.scope, auth.permissions).map(item => ({ ...item, icon: icons[item.icon] })))
const identityLabel = computed(() => roleSummary(auth.roles, auth.scope))

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
      <div class="sidebar__user"><span>SF</span><div><b>{{ auth.currentUser?.displayName || auth.currentUser?.username }}</b><small>{{ identityLabel }}</small></div></div>
      <button type="button" :disabled="auth.loading" @click="signOut"><LogOut :size="17" /> {{ auth.loading ? '退出中…' : '安全退出' }}</button>
    </div>
  </aside>
</template>
