<script setup lang="ts">
import { useNotificationStore } from '@/stores/notifications'
import { CheckCircle2, CircleAlert, Info, X } from '@lucide/vue'
import CopyTextButton from './CopyTextButton.vue'

const notifications = useNotificationStore()
const icons = { success: CheckCircle2, error: CircleAlert, info: Info }
</script>
<template>
  <div class="notice-host" aria-live="polite" aria-relevant="additions">
    <section v-for="notice in notifications.notices" :key="notice.id" class="alert notice" :class="`alert--${notice.kind}`" :role="notice.kind === 'error' ? 'alert' : 'status'">
      <component :is="icons[notice.kind]" :size="18" aria-hidden="true" />
      <div><b v-if="notice.code">{{ notice.code }}：</b>{{ notice.message }}<p v-if="notice.traceId" class="trace-line">追踪编号：<code>{{ notice.traceId }}</code> <CopyTextButton :value="notice.traceId" label="复制 Trace ID" /></p></div>
      <button class="icon-button" type="button" :aria-label="`关闭通知：${notice.message}`" @click="notifications.remove(notice.id)"><X :size="16" /></button>
    </section>
  </div>
</template>
