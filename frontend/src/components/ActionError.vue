<script setup lang="ts">
import { ref } from 'vue'

const props = defineProps<{ message: string; code?: string; traceId?: string }>()
const copyMessage = ref('')

async function copyTraceId() {
  if (!props.traceId) return
  try {
    await navigator.clipboard.writeText(props.traceId)
    copyMessage.value = '追踪编号已复制。'
  } catch {
    copyMessage.value = '复制失败，请手动记录追踪编号。'
  }
}
</script>

<template>
  <div class="alert alert--error" role="alert">
    <b v-if="code">{{ code }}：</b>{{ message }}
    <span v-if="traceId">追踪编号：{{ traceId }} <button class="text-button" type="button" @click="copyTraceId">复制</button></span>
    <small v-if="copyMessage" role="status">{{ copyMessage }}</small>
  </div>
</template>
