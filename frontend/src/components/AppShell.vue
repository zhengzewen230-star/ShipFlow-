<script setup lang="ts">
import { ref } from 'vue'
import Sidebar from './Sidebar.vue'
import TopBar from './TopBar.vue'
import WorkspaceTabs from './WorkspaceTabs.vue'
import PageHeader from './PageHeader.vue'
const navigationOpen = ref(false)
</script>
<template>
  <div class="app-shell" :class="{ 'navigation-open': navigationOpen }" @keydown.esc="navigationOpen = false">
    <button class="mobile-nav-backdrop" type="button" aria-label="关闭业务导航" @click="navigationOpen = false" @keydown.enter.prevent="navigationOpen = false" @keydown.space.prevent="navigationOpen = false"></button>
    <Sidebar />
    <div class="app-main">
      <a class="skip-link" href="#main-content">跳到主要内容</a>
      <TopBar @toggle-navigation="navigationOpen = !navigationOpen" />
      <WorkspaceTabs />
      <PageHeader />
      <main id="main-content" class="app-content" tabindex="-1"><slot /></main>
    </div>
  </div>
</template>
