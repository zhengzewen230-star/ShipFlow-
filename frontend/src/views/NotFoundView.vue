<script setup lang="ts">
import { ArrowLeft, Compass } from '@lucide/vue'
</script>

<template>
  <main class="not-found"><div class="not-found__graphic"><span>4</span><Compass :size="80" /><span>4</span></div><span class="kicker">路线偏离</span><h1>没有找到这个页面</h1><p>地址可能已变更，或该业务入口尚未开放。</p><RouterLink class="btn btn--primary" to="/"><ArrowLeft :size="17" /> 返回 ShipFlow 首页</RouterLink></main>
</template>
