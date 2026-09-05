<script setup lang="ts">
import { ArrowUpRight } from '@lucide/vue'
defineProps<{ label: string; value: number | string; hint?: string; definition?: string | null; source?: string | null; to: string | object; tone?: 'default' | 'warning' | 'danger' }>()
</script>
<template>
  <RouterLink class="metric-card" :class="`metric-card--${tone || 'default'}`" :to="to">
    <span class="metric-card__icon"><slot name="icon" /></span><div><small>{{ label }}</small><strong>{{ value }}</strong><em>{{ hint }}</em><span v-if="definition" class="metric-card__definition">{{ definition }}</span><span v-if="source" class="metric-card__source">来源：{{ source }}</span></div><ArrowUpRight :size="16" aria-hidden="true" />
  </RouterLink>
</template>
