<script setup lang="ts">
import { ChevronRight } from '@lucide/vue'
defineProps<{ items: Array<{ key: string; label: string; count: number; to: string | object; priority?: string }> }>()
</script>
<template><section class="erp-panel"><header><div><h2>我的待办</h2><p>按优先级进入对应业务列表</p></div><strong>{{ items.reduce((sum, item) => sum + item.count, 0) }} 项</strong></header><div v-if="items.length" class="work-queue"><RouterLink v-for="item in items" :key="item.key" :to="item.to"><span class="work-queue__priority">{{ item.priority || '待处理' }}</span><strong>{{ item.label }}</strong><b>{{ item.count }}</b><ChevronRight :size="15" /></RouterLink></div><p v-else class="erp-empty-inline">当前没有待办，所有任务已处理。</p></section></template>
