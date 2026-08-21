<script setup lang="ts">
import { ref } from 'vue'
import EmptyState from './EmptyState.vue'
const props = defineProps<{ loading?: boolean; error?: string; empty?: boolean; emptyTitle?: string; traceId?: string; retry?: () => void | Promise<void> }>()
const copied = ref(false)
const copyMessage = ref('')
const retrying = ref(false)
async function copyTraceId() {
  if (!props.traceId) return
  copyMessage.value = ''
  try {
    await navigator.clipboard.writeText(props.traceId)
    copied.value = true
    copyMessage.value = '追踪编号已复制。'
  } catch {
    copied.value = false
    copyMessage.value = '复制失败，请手动记录追踪编号。'
  }
}
async function retryRequest() {
  if (!props.retry || retrying.value) return
  retrying.value = true
  try { await props.retry() } finally { retrying.value = false }
}
</script>
<template>
  <div v-if="loading" class="data-state" role="status">正在加载…</div>
  <div v-else-if="error" class="alert alert--error" role="alert">
    <p>{{ error }}</p>
    <p v-if="traceId">追踪编号：{{ traceId }} <button class="text-button" type="button" @click="copyTraceId">复制</button></p>
    <small v-if="copyMessage || copied" role="status">{{ copyMessage || '追踪编号已复制。' }}</small>
    <button v-if="retry" class="btn btn--secondary" type="button" :disabled="retrying" @click="retryRequest">{{ retrying ? '重试中…' : '重试' }}</button>
  </div>
  <EmptyState v-else-if="empty" :title="emptyTitle || '暂无数据'" description="当前筛选条件下没有可显示的记录。" />
  <slot v-else />
</template>
