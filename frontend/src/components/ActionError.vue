<script setup lang="ts">
import { CircleAlert } from '@lucide/vue'
import CopyTextButton from './CopyTextButton.vue'

defineProps<{ message: string; code?: string; traceId?: string }>()
</script>

<template>
  <div class="alert alert--error" role="alert">
    <CircleAlert :size="18" aria-hidden="true" />
    <span><b v-if="code">{{ code }}：</b>{{ message }}</span>
    <span v-if="traceId" class="trace-line">追踪编号：<code>{{ traceId }}</code> <CopyTextButton :value="traceId" label="复制 Trace ID" /></span>
  </div>
</template>
