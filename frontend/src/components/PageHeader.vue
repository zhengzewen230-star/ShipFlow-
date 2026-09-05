<script setup lang="ts">
import { computed } from 'vue'
import { ChevronRight, RefreshCw } from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import { consoleNavigationItems } from '@/navigation/console'

const route = useRoute()
const router = useRouter()
const item = computed(() => consoleNavigationItems.find(entry => route.path === entry.path || (entry.path !== '/app' && route.path.startsWith(`${entry.path}/`))))
const title = computed(() => item.value?.label || '业务工作台')
const context = computed(() => Object.entries(route.query).filter(([, value]) => value != null && value !== '').slice(0, 4))
</script>

<template>
  <header class="erp-page-header">
    <div>
      <nav class="erp-breadcrumb" aria-label="面包屑"><RouterLink to="/app">ShipFlow</RouterLink><ChevronRight :size="13" /><span>{{ title }}</span></nav>
      <div class="erp-page-title"><h1>{{ title }}</h1><span v-if="context.length">当前筛选：{{ context.map(([key, value]) => `${key}=${value}`).join(' · ') }}</span><span v-else>全部可访问数据</span></div>
    </div>
    <div class="erp-page-actions"><button class="btn btn--secondary" type="button" aria-label="刷新当前页面" @click="router.go(0)"><RefreshCw :size="15" />刷新</button></div>
  </header>
</template>
