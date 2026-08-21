<script setup lang="ts">
import { ref } from 'vue'
import { Calculator, ClipboardCheck, MapPinned, ReceiptText, Warehouse } from '@lucide/vue'
import { useReveal } from '@/composables/useReveal'

const section = ref<HTMLElement | null>(null)
const { revealed } = useReveal(section)
const steps = [[Calculator, '获取报价', '路线与货况'], [ClipboardCheck, '创建订单', '锁定方案'], [Warehouse, '仓库操作', '复称出库'], [MapPinned, '物流追踪', '节点同步'], [ReceiptText, '账单对账', '费用留痕']] as const
</script>

<template>
  <section ref="section" class="light-journey" :class="{ 'is-revealed': revealed }"><div class="container"><div class="light-heading"><span>从报价到签收</span><h2>每一个节点，都为下一步协同准备</h2></div><div class="light-journey__flow"><i class="light-journey__line"></i><article v-for="([icon, title, text], index) in steps" :key="title" :style="{ '--delay': `${index * 100}ms` }"><span><component :is="icon" :size="20" /></span><div><small>0{{ index + 1 }}</small><b>{{ title }}</b><em>{{ text }}</em></div></article></div></div></section>
</template>
