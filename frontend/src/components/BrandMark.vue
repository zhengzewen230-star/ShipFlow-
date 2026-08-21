<script setup lang="ts">
defineProps<{ compact?: boolean }>()
</script>

<template>
  <RouterLink class="brand" to="/" aria-label="ShipFlow 首页">
    <span class="brand__mark" aria-hidden="true"><span></span><span></span><span></span></span>
    <span v-if="!compact" class="brand__name">Ship<span>Flow</span></span>
  </RouterLink>
</template>

<style scoped>
.brand { display: inline-flex; align-items: center; gap: .7rem; color: var(--navy-950); font-weight: 800; font-size: 1.35rem; letter-spacing: -.03em; }
.brand__name span { color: var(--cyan-500); }
.brand__mark { position: relative; width: 2.15rem; height: 2.15rem; display: grid; place-items: center; border-radius: .65rem; background: var(--navy-950); overflow: hidden; transform: rotate(-5deg); }
.brand__mark span { position: absolute; height: 3px; border-radius: 4px; background: var(--cyan-400); transform: rotate(-30deg); }
.brand__mark span:nth-child(1) { width: 1.45rem; top: .58rem; left: .34rem; }
.brand__mark span:nth-child(2) { width: 1.15rem; top: 1rem; left: .58rem; background: white; }
.brand__mark span:nth-child(3) { width: .9rem; top: 1.42rem; left: .72rem; background: var(--orange-400); }
</style>
