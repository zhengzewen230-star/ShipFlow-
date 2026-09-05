<script setup lang="ts">
import { ref } from 'vue'
import { Check, Copy } from '@lucide/vue'

const props = withDefaults(defineProps<{ value?: string | number | null; label?: string; compact?: boolean }>(), {
  label: '复制',
  compact: true,
})
const copied = ref(false)
const message = ref('')

async function copy() {
  if (props.value == null || String(props.value).trim() === '') return
  try {
    await navigator.clipboard.writeText(String(props.value))
    copied.value = true
    message.value = `${props.label}成功`
    window.setTimeout(() => { copied.value = false }, 2_000)
  } catch {
    copied.value = false
    message.value = '复制失败，请手动选择内容。'
  }
}
</script>

<template>
  <span class="copy-control">
    <button class="text-button copy-control__button" type="button" :disabled="value == null || String(value).trim() === ''" :aria-label="`${label}：${value ?? ''}`" @click="copy">
      <Check v-if="copied" :size="14" aria-hidden="true" />
      <Copy v-else :size="14" aria-hidden="true" />
      <span v-if="!compact">{{ copied ? '已复制' : label }}</span>
    </button>
    <small class="sr-only" role="status">{{ message }}</small>
  </span>
</template>
