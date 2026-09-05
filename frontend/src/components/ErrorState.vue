<script setup lang="ts">
import DataState from './DataState.vue'
defineProps<{ message: string; code?: string; traceId?: string; status?: number; retry?: () => void | Promise<void> }>()
</script>
<template><DataState :error="message" :error-code="code" :trace-id="traceId" :status="status" :retry="retry" /></template>
