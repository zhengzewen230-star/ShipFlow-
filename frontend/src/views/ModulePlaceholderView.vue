<script setup lang="ts">
import EmptyState from '@/components/EmptyState.vue'

defineProps<{ title: string; description: string }>()
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="kicker">业务模块</span><h1>{{ title }}</h1><p>{{ description }}</p></div></div>
    <div class="panel module-panel"><EmptyState :title="`${title}尚未接入`" description="本阶段仅建立信息架构与路由空状态，后续将依据 OpenAPI 契约逐项实现接口与数据库双重验证。" action-label="先体验在线报价" /></div>
  </section>
</template>
