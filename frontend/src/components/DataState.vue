<script setup lang="ts">
import EmptyState from './EmptyState.vue'
defineProps<{ loading?: boolean; error?: string; empty?: boolean; emptyTitle?: string }>()
</script>
<template>
  <div v-if="loading" class="data-state" role="status">正在加载…</div>
  <div v-else-if="error" class="alert alert--error" role="alert">{{ error }}</div>
  <EmptyState v-else-if="empty" :title="emptyTitle || '暂无数据'" description="当前筛选条件下没有可显示的记录。" />
  <slot v-else />
</template>
