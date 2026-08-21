<script setup lang="ts">
import type { Component } from 'vue'

defineProps<{ icon: Component; title: string; description: string; tag: string }>()
</script>

<template>
  <article class="service-card">
    <div class="service-card__icon"><component :is="icon" :size="25" /></div>
    <span class="service-card__tag">{{ tag }}</span>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <RouterLink to="/quote">获取方案 <span>→</span></RouterLink>
  </article>
</template>
