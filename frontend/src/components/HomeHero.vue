<script setup lang="ts">
import { ArrowRight, CheckCircle2, Globe2, ShieldCheck } from '@lucide/vue'
import WorldMap from './WorldMap.vue'

const reducedMotion = typeof window !== 'undefined' && window.matchMedia('(prefers-reduced-motion: reduce)').matches
</script>

<template>
  <section class="glass-hero" aria-labelledby="hero-title">
    <div class="glass-hero__port" aria-hidden="true"></div><div class="glass-hero__wash" aria-hidden="true"></div>
    <div class="container glass-hero__layout">
      <div class="glass-hero__copy">
        <span class="glass-hero__eyebrow">SHIPFLOW · CROSS-BORDER LOGISTICS</span>
        <h1 id="hero-title">让跨境发货，<em>更轻松也更安心</em></h1>
        <p>为跨境电商商户和团队梳理发货安排、运输进度与协作事项，让每一次履约都有更清晰的方向。</p>
        <div class="glass-hero__actions"><RouterLink class="glass-btn glass-btn--primary" to="/quote">在线预估运费 <ArrowRight :size="17" /></RouterLink><RouterLink class="glass-btn glass-btn--secondary" to="/apply">获取正式报价</RouterLink><RouterLink class="glass-btn glass-btn--secondary" to="/login">登录控制台</RouterLink></div>
        <div class="glass-hero__assurance"><span><CheckCircle2 :size="16" /> 面向企业团队</span><span><ShieldCheck :size="16" /> 清晰服务体验</span></div>
      </div>
      <div class="logistics-console" aria-label="全球服务覆盖示意面板">
        <div class="logistics-console__head"><div><small>SHIPFLOW / SERVICE NETWORK</small><b>全球服务覆盖示意</b></div><span><i></i> 仅作服务展示</span></div>
        <WorldMap :animated="!reducedMotion" />
        <div class="logistics-console__status"><span><Globe2 :size="17" /><b>主要区域</b><small>美国、加拿大、欧洲、澳大利亚</small></span><span><CheckCircle2 :size="17" /><b>东亚连接</b><small>日本、韩国等重点方向</small></span></div>
        <div class="logistics-console__foot"><span>服务区域与线路为展示示意</span><i>不代表实时运输信息</i></div>
      </div>
    </div>
  </section>
</template>
