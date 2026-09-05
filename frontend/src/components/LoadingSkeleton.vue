<script setup lang="ts">
withDefaults(defineProps<{ rows?: number }>(), { rows: 4 })
</script>
<template><div class="erp-skeleton" role="status" aria-live="polite" aria-label="正在加载"><span v-for="row in rows" :key="row" :style="{ width: `${100 - (row % 3) * 12}%` }"></span></div></template>
