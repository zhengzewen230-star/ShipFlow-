<script setup lang="ts">
import { computed, ref } from 'vue'
import { Bell, ChevronDown, Menu, Search, X } from '@lucide/vue'
import { useRouter } from 'vue-router'
import { useAuthStore } from '@/stores/auth'

const emit = defineEmits<{ toggleNavigation: [] }>()
const router = useRouter()
const auth = useAuthStore()
const query = ref('')
const accountOpen = ref(false)
const scopeLabel = computed(() => auth.scope === 'PLATFORM'
  ? '平台全局范围'
  : auth.tenantId ? `租户 ${auth.tenantId} · 授权店铺` : '租户授权范围')

function search() {
  const keyword = query.value.trim()
  if (!keyword) return
  void router.push({ path: '/app/orders', query: { orderNo: keyword } })
}
</script>

<template>
  <header class="erp-topbar">
    <button class="icon-button erp-menu-button" type="button" aria-label="展开业务导航" @click="emit('toggleNavigation')" @keydown.enter.prevent="emit('toggleNavigation')" @keydown.space.prevent="emit('toggleNavigation')"><Menu :size="18" /></button>
    <div class="erp-scope" aria-label="当前数据范围">
      <span>当前范围</span><strong>{{ scopeLabel }}</strong>
    </div>
    <form class="erp-global-search" role="search" @submit.prevent="search">
      <Search :size="17" aria-hidden="true" />
      <label class="sr-only" for="erp-global-search">全局搜索订单号</label>
      <input id="erp-global-search" v-model="query" autocomplete="off" placeholder="搜索订单号，回车进入订单列表" @keydown.enter.prevent="search" />
      <button v-if="query" type="button" aria-label="清空搜索" @click="query = ''"><X :size="15" /></button>
    </form>
    <div class="erp-topbar__tools">
      <span class="erp-timezone" title="所有业务时间按此时区展示">UTC+08:00 · Asia/Shanghai</span>
      <button class="icon-button" type="button" aria-label="通知中心，当前无未读通知"><Bell :size="18" /><span class="sr-only">当前无未读通知</span></button>
      <div class="erp-account">
        <button class="erp-account__trigger" type="button" :aria-expanded="accountOpen" aria-haspopup="menu" @click="accountOpen = !accountOpen">
          <span class="erp-avatar">{{ (auth.currentUser?.displayName || auth.currentUser?.username || 'SF').slice(0, 1) }}</span>
          <span><strong>{{ auth.currentUser?.displayName || auth.currentUser?.username }}</strong><small>{{ auth.scope === 'PLATFORM' ? '平台账号' : '租户账号' }}</small></span>
          <ChevronDown :size="15" />
        </button>
        <div v-if="accountOpen" class="erp-account__menu" role="menu">
          <button role="menuitem" type="button" :disabled="auth.loading" @click="$emit('toggleNavigation'); accountOpen = false">查看业务导航</button>
          <button role="menuitem" type="button" :disabled="auth.loading" @click="auth.logout().then(() => router.replace('/login'))">{{ auth.loading ? '退出中…' : '安全退出' }}</button>
        </div>
      </div>
    </div>
  </header>
</template>
