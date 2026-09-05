<script setup lang="ts">
import { ref, watch } from 'vue'
import { X } from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import { consoleNavigationItems } from '@/navigation/console'

interface WorkspaceTab { path: string; label: string }
const STORAGE_KEY = 'shipflow:workspace-tabs'
const route = useRoute()
const router = useRouter()
const stored = (() => {
  try { return JSON.parse(sessionStorage.getItem(STORAGE_KEY) || '[]') as WorkspaceTab[] } catch { return [] }
})()
const tabs = ref<WorkspaceTab[]>(stored)
function persist(value: WorkspaceTab[]) { tabs.value = value; sessionStorage.setItem(STORAGE_KEY, JSON.stringify(value)) }

function labelFor(path: string) {
  const base = path.split('?')[0]
  return consoleNavigationItems.find(item => item.path === base)?.label
    ?? (base.includes('/stores/') ? '店铺详情' : base.includes('/logistics/') ? '物流渠道详情' : '业务页面')
}
function remember(path: string) {
  if (!path.startsWith('/app')) return
  const next = tabs.value.filter(item => item.path !== path)
  next.push({ path, label: labelFor(path) })
  persist(next.slice(-8))
}
function close(path: string) {
  const index = tabs.value.findIndex(item => item.path === path)
  const next = tabs.value.filter(item => item.path !== path)
  persist(next)
  if (route.fullPath === path) void router.push(next[Math.max(0, index - 1)]?.path || '/app')
}
watch(() => route.fullPath, remember, { immediate: true })
</script>

<template>
  <nav class="workspace-tabs" aria-label="最近访问页面">
    <RouterLink v-for="tab in tabs" :key="tab.path" :to="tab.path" class="workspace-tab" :class="{ 'is-active': route.fullPath === tab.path }">
      <span>{{ tab.label }}</span>
      <button v-if="tab.path !== '/app'" type="button" :aria-label="`关闭${tab.label}页签`" @click.prevent.stop="close(tab.path)"><X :size="13" /></button>
    </RouterLink>
  </nav>
</template>
