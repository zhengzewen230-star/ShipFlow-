<script setup lang="ts">
import { Bell, Search } from '@lucide/vue'
import Sidebar from '@/components/Sidebar.vue'
import { useAuthStore } from '@/stores/auth'

const auth = useAuthStore()
</script>

<template>
  <div class="app-shell">
    <Sidebar />
    <div class="app-main">
      <header class="app-topbar">
        <label class="app-search"><Search :size="17" /><input aria-label="页面内搜索" placeholder="在当前业务页面中查找" /></label>
        <div class="app-topbar__right">
          <span class="environment">{{ auth.scope === 'PLATFORM' ? '平台工作台' : '商户工作台' }}</span>
          <button class="icon-button" type="button" aria-label="通知"><Bell :size="19" /></button>
        </div>
      </header>
      <main class="app-content"><RouterView /></main>
    </div>
  </div>
</template>
