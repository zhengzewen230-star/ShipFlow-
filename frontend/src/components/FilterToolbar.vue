<script setup lang="ts">
import { ref } from 'vue'
import { ChevronDown, Filter, RotateCcw, Search } from '@lucide/vue'
defineProps<{ submitting?: boolean; advanced?: boolean }>()
const emit = defineEmits<{ search: []; reset: [] }>()
const expanded = ref(false)
</script>
<template>
  <section class="filter-toolbar" aria-label="列表筛选">
    <div class="filter-toolbar__main"><Filter :size="16" aria-hidden="true" /><slot /></div>
    <div class="filter-toolbar__actions">
      <button v-if="advanced" class="btn btn--ghost" type="button" :aria-expanded="expanded" @click="expanded = !expanded">高级筛选<ChevronDown :size="14" /></button>
      <button class="btn btn--secondary" type="button" :disabled="submitting" @click="emit('reset')"><RotateCcw :size="14" />重置</button>
      <button class="btn btn--primary" type="button" :disabled="submitting" @click="emit('search')"><Search :size="14" />{{ submitting ? '查询中…' : '查询' }}</button>
    </div>
    <div v-if="advanced && expanded" class="filter-toolbar__advanced"><slot name="advanced" /></div>
  </section>
</template>
