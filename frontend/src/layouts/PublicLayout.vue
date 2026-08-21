<script setup lang="ts">
import Navbar from '@/components/Navbar.vue'
</script>

<template>
  <div class="public-layout">
    <Navbar />
    <main><RouterView /></main>
  </div>
</template>
