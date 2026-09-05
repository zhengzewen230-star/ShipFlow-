<script setup lang="ts">
import CopyTextButton from './CopyTextButton.vue'
defineProps<{ value?: string | number | null; label?: string }>()
</script>
<template><span class="copyable-id"><code>{{ value || '—' }}</code><CopyTextButton v-if="value" :value="String(value)" :label="label || '编号'" /></span></template>
