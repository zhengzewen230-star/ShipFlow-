<script setup lang="ts">
import { ref } from 'vue'
import { Compass, Globe2, MapPin } from '@lucide/vue'
import { useReveal } from '@/composables/useReveal'
import WorldMap from './WorldMap.vue'

const section = ref<HTMLElement | null>(null)
const { revealed } = useReveal(section)
</script>

<template>
  <section id="channels" ref="section" class="light-network" :class="{ 'is-revealed': revealed }">
    <div class="container light-network__layout">
      <div><span class="light-kicker">SERVICE NETWORK</span><h2>面向主要跨境方向的<br />服务连接</h2><p>从东亚出发，围绕北美、欧洲与澳大利亚等商户常用方向，帮助团队更从容地规划每一次发货。</p><div class="light-network__metrics"><span><Globe2 :size="18" /> 全球服务视野</span><span><MapPin :size="18" /> 重点方向示意</span><span><Compass :size="18" /> 清晰安排发货</span></div></div>
      <div class="light-network__card"><WorldMap :animated="revealed" /><small>服务覆盖示意 · 不代表精确覆盖范围或实时物流数据</small></div>
    </div>
  </section>
</template>
