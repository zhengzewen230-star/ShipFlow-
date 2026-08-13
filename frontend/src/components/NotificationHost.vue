<script setup lang="ts">
import { useNotificationStore } from '@/stores/notifications'

const notifications = useNotificationStore()
</script>
<template><div class="notice-host" aria-live="polite"><button v-for="notice in notifications.notices" :key="notice.id" type="button" class="alert" :class="`alert--${notice.kind}`" @click="notifications.remove(notice.id)">{{ notice.message }}</button></div></template>
