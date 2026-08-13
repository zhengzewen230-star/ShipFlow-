<script setup lang="ts">
import { Inbox } from '@lucide/vue'

withDefaults(defineProps<{ title?: string; description?: string; actionLabel?: string }>(), {
  title: '暂无业务数据',
  description: '此模块的业务详情将在后续阶段按契约逐步接入。',
})
</script>

<template>
  <div class="empty-state">
    <span class="empty-state__icon"><Inbox :size="30" /></span>
    <h3>{{ title }}</h3>
    <p>{{ description }}</p>
    <RouterLink v-if="actionLabel" class="btn btn--secondary" to="/quote">{{ actionLabel }}</RouterLink>
  </div>
</template>
