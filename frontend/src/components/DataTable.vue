<script setup lang="ts">
defineProps<{ caption: string; busy?: boolean }>()
</script>
<template>
  <div class="erp-table" :aria-busy="busy">
    <div class="erp-table__toolbar"><strong>{{ caption }}</strong><slot name="tools" /></div>
    <div class="data-table-wrap"><table class="data-table"><caption class="sr-only">{{ caption }}</caption><slot /></table></div>
    <slot name="footer" />
  </div>
</template>
