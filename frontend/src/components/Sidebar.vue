<script setup lang="ts">
import { computed, ref } from 'vue'
import { useRouter } from 'vue-router'
import {
  BarChart3, Building2, Calculator, ClipboardList, Warehouse, MapPinned,
  CircleAlert, ReceiptText, Database, ScrollText, LogOut, ShieldCheck, Store, Users,
} from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import { consoleGroupLabels, roleSummary, visibleConsoleItems, type ConsoleIcon, type ConsoleNavigationItem } from '@/navigation/console'
import BrandMark from './BrandMark.vue'

const router = useRouter()
const auth = useAuthStore()
const icons: Record<ConsoleIcon, typeof BarChart3> = { overview: BarChart3, tenant: Building2, users: Users, quote: Calculator, store: Store, rbac: ShieldCheck, logistics: Database, order: ClipboardList, warehouse: Warehouse, tracking: MapPinned, exception: CircleAlert, billing: ReceiptText, audit: ScrollText }
const visibleItems = computed(() => visibleConsoleItems(auth.scope, auth.permissions).map(item => ({ ...item, icon: icons[item.icon] })))
const groupOrder: ConsoleNavigationItem['group'][] = ['workspace', 'master-data', 'fulfillment', 'settlement', 'governance']
const expanded = ref(new Set<ConsoleNavigationItem['group']>(groupOrder))
const groups = computed(() => groupOrder.map(group => ({ group, label: consoleGroupLabels[group], items: visibleItems.value.filter(item => item.group === group) })).filter(group => group.items.length))
const identityLabel = computed(() => roleSummary(auth.roles, auth.scope))

async function signOut() {
  await auth.logout()
  await router.replace('/login')
}
function toggle(group: ConsoleNavigationItem['group']) {
  const next = new Set(expanded.value)
  next.has(group) ? next.delete(group) : next.add(group)
  expanded.value = next
}
</script>

<template>
  <aside class="sidebar" aria-label="ShipFlow ERP 业务导航">
    <div class="sidebar__brand"><BrandMark /></div>
    <nav aria-label="一级及二级业务导航">
      <section v-for="group in groups" :key="group.group" class="sidebar-group">
        <button class="sidebar-group__toggle" type="button" :aria-expanded="expanded.has(group.group)" @click="toggle(group.group)"><span>{{ group.label }}</span><small>{{ expanded.has(group.group) ? '收起' : '展开' }}</small></button>
        <div v-show="expanded.has(group.group)" class="sidebar-group__items">
          <RouterLink v-for="item in group.items" :key="item.path" :to="item.path" :class="{ exact: item.path === '/app' }">
            <component :is="item.icon" :size="17" aria-hidden="true" /><span>{{ item.label }}</span>
          </RouterLink>
        </div>
      </section>
    </nav>
    <div class="sidebar__footer">
      <div class="sidebar__user"><span>SF</span><div><b>{{ auth.currentUser?.displayName || auth.currentUser?.username }}</b><small>{{ identityLabel }}</small></div></div>
      <button type="button" :disabled="auth.loading" @click="signOut"><LogOut :size="17" /> {{ auth.loading ? '退出中…' : '安全退出' }}</button>
    </div>
  </aside>
</template>
