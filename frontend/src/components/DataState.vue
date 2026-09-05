<script setup lang="ts">
import { ref } from 'vue'
import { CircleAlert, LockKeyhole, LogIn } from '@lucide/vue'
import EmptyState from './EmptyState.vue'
import CopyTextButton from './CopyTextButton.vue'
const props = defineProps<{ loading?: boolean; error?: string; errorCode?: string; status?: number; empty?: boolean; emptyTitle?: string; traceId?: string; retry?: () => void | Promise<void> }>()
const retrying = ref(false)
async function retryRequest() {
  if (!props.retry || retrying.value) return
  retrying.value = true
  try { await props.retry() } finally { retrying.value = false }
}
</script>
<template>
  <div v-if="loading" class="data-state data-state--loading" role="status" aria-live="polite" aria-busy="true">
    <span class="loading-spinner" aria-hidden="true"></span><span>正在加载，请稍候…</span>
    <span class="loading-skeleton" aria-hidden="true"></span><span class="loading-skeleton loading-skeleton--short" aria-hidden="true"></span>
  </div>
  <div v-else-if="error" class="alert alert--error data-state--error" role="alert">
    <component :is="status === 401 ? LogIn : status === 403 ? LockKeyhole : CircleAlert" :size="22" aria-hidden="true" />
    <div>
      <b>{{ status === 401 ? '会话已过期' : status === 403 ? '无权访问' : '请求失败' }}</b>
      <p><strong v-if="errorCode">{{ errorCode }}：</strong>{{ error }}</p>
      <p v-if="traceId" class="trace-line">追踪编号：<code>{{ traceId }}</code> <CopyTextButton :value="traceId" label="复制 Trace ID" /></p>
    </div>
    <button v-if="retry" class="btn btn--secondary" type="button" :disabled="retrying" @click="retryRequest">{{ retrying ? '重试中…' : '重试' }}</button>
    <RouterLink v-if="status === 401" class="btn btn--primary" :to="{ name: 'login' }">重新登录</RouterLink>
  </div>
  <EmptyState v-else-if="empty" :title="emptyTitle || '暂无数据'" description="当前筛选条件下没有可显示的记录。" />
  <slot v-else />
</template>
