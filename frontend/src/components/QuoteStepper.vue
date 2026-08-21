<script setup lang="ts">
defineProps<{ steps: string[]; current: number }>()
</script>

<template>
  <ol class="stepper" aria-label="报价步骤">
    <li v-for="(step, index) in steps" :key="step" :class="{ active: current === index, done: current > index }">
      <span>{{ current > index ? '✓' : index + 1 }}</span><b>{{ step }}</b>
    </li>
  </ol>
</template>
