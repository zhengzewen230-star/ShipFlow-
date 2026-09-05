<script setup lang="ts">
import { ChevronRight } from '@lucide/vue'
defineProps<{ items: Array<{ id: string; level: string; title: string; description: string; occurredAt: string; to: string | object }> }>()
</script>
<template><section class="erp-panel"><header><div><h2>风险提醒</h2><p>来自订单、轨迹、异常和费用结算</p></div><strong>{{ items.length }} 条</strong></header><div v-if="items.length" class="risk-panel"><RouterLink v-for="item in items" :key="item.id" :to="item.to"><span class="risk-panel__level" :data-level="item.level">{{ item.level }}</span><div><strong>{{ item.title }}</strong><p>{{ item.description }}</p><time>{{ item.occurredAt }}</time></div><ChevronRight :size="15" /></RouterLink></div><p v-else class="erp-empty-inline">当前没有需要关注的风险。</p></section></template>
