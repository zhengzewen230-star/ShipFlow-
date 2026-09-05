<script setup lang="ts">
import { computed } from 'vue'
import { CheckCircle2, CircleAlert, Clock3, CircleDot, XCircle } from '@lucide/vue'
import { displayValue } from '@/utils/display'

const props = defineProps<{ status?: string | null; label?: string }>()
const normalized = computed(() => (props.status ?? '').toUpperCase())
const text = computed(() => {
  if (props.label) return props.label
  const raw = String(props.status ?? '').trim()
  return /[\u4e00-\u9fff]/.test(raw) ? raw : displayValue('status', raw)
})
const icon = computed(() => {
  if (/(SUCCESS|CONFIRMED|RESOLVED|CLOSED|DELIVERED|ACTIVE|AUTO_CLOSED)/.test(normalized.value)) return CheckCircle2
  if (/(FAILED|REJECTED|CANCELLED|DISABLED|ERROR)/.test(normalized.value)) return XCircle
  if (/(PENDING|PROCESSING|OPEN|WAITING|IN_TRANSIT)/.test(normalized.value)) return Clock3
  if (/(WARNING|EXCEPTION|PARTIAL)/.test(normalized.value)) return CircleAlert
  return CircleDot
})
</script>

<template>
  <span class="status-badge" :data-status="normalized" role="status" :aria-label="`状态：${text}`">
    <component :is="icon" :size="13" aria-hidden="true" />
    {{ text }}
  </span>
</template>
