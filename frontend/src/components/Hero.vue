<script setup lang="ts">
import { ArrowRight, CheckCircle2, Clock3, Globe2, PackageCheck } from '@lucide/vue'
</script>

<template>
  <section class="hero">
    <div class="hero__glow hero__glow--one"></div>
    <div class="hero__glow hero__glow--two"></div>
    <div class="container hero__grid">
      <div class="hero__content">
        <div class="eyebrow"><Globe2 :size="15" /> 跨境物流数字化协作平台</div>
        <h1>让每一次跨境发运，<span>报价清晰，履约可见</span></h1>
        <p class="hero__lead">ShipFlow 将渠道报价、订单、仓库复称、物流轨迹与账单对账连接在一处，为跨境商家提供更稳定的履约视野。</p>
        <div class="hero__actions">
          <RouterLink class="btn btn--primary btn--large" to="/quote">立即获取报价 <ArrowRight :size="18" /></RouterLink>
          <a class="text-link" href="#capabilities">查看平台能力</a>
        </div>
        <div class="hero__assurances">
          <span><CheckCircle2 :size="16" /> 多渠道对比</span>
          <span><CheckCircle2 :size="16" /> 全程节点可查</span>
          <span><CheckCircle2 :size="16" /> 账单差异闭环</span>
        </div>
      </div>
      <div class="hero-visual" aria-label="跨境物流流程示意">
        <div class="route-card">
          <div class="route-card__top"><span>运输进行中</span><span class="status-dot">正常</span></div>
          <div class="route-map">
            <div class="route-map__line"><i></i></div>
            <div class="route-point route-point--start"><b>深圳</b><small>仓库已出库</small></div>
            <div class="route-plane">✦</div>
            <div class="route-point route-point--end"><b>洛杉矶</b><small>预计 08.16 抵达</small></div>
          </div>
          <div class="route-metrics">
            <div><Clock3 :size="18" /><span><small>预计时效</small><b>5–7 天</b></span></div>
            <div><PackageCheck :size="18" /><span><small>当前节点</small><b>干线运输</b></span></div>
          </div>
        </div>
        <div class="quote-float"><small>本周最优渠道</small><strong>空运专线</strong><span>综合成本下降 <b>12%</b></span></div>
      </div>
    </div>
  </section>
</template>
