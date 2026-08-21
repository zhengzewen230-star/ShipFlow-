<script setup lang="ts">
import { ref } from 'vue'
import { BadgeDollarSign, BellRing, Boxes, CheckCircle2, ClipboardList, FileText, MapPinned, ShieldAlert, Truck, UsersRound } from '@lucide/vue'
import { useReveal } from '@/composables/useReveal'
import HomeHero from '@/components/HomeHero.vue'
import GlobalNetwork from '@/components/GlobalNetwork.vue'
import BrandMark from '@/components/BrandMark.vue'

const valueSection = ref<HTMLElement | null>(null)
const processSection = ref<HTMLElement | null>(null)
const solutionsSection = ref<HTMLElement | null>(null)
const workspaceSection = ref<HTMLElement | null>(null)
const trustSection = ref<HTMLElement | null>(null)
const { revealed: valuesRevealed } = useReveal(valueSection)
const { revealed: processRevealed } = useReveal(processSection)
const { revealed: solutionsRevealed } = useReveal(solutionsSection)
const { revealed: workspaceRevealed } = useReveal(workspaceSection)
const { revealed: trustRevealed } = useReveal(trustSection)

const values = [
  [BadgeDollarSign, '价格更透明', '提前了解运输费用与服务选择，减少沟通成本，让每一次发货都更安心。'],
  [Truck, '发货更省心', '围绕常用跨境方向提供清晰安排，让团队更从容地推进日常发货。'],
  [MapPinned, '进度看得见', '重要运输节点清晰可见，团队与客户都能及时掌握包裹动态。'],
  [ShieldAlert, '问题有响应', '遇到需要关注的情况更早发现、更快处理，减少不确定性带来的影响。'],
] as const

const steps = [
  ['01', ClipboardList, '提交运输需求', '告诉我们起运地、目的地和货物情况，快速开始一次询价。'],
  ['02', CheckCircle2, '获得合适方案', '结合你的发货偏好，查看清晰的服务选择并做出安排。'],
  ['03', UsersRound, '全程跟进与协同', '从发货到签收，团队可在同一处持续跟进和沟通。'],
] as const

const solutions = [
  [UsersRound, '多店铺协同', '为不同店铺和成员留出清晰分工，让发货安排更有条理。', '协作视图'],
  [Truck, '国际运输安排', '集中查看不同发货方向的服务选择，减少反复确认。', '运输选择'],
  [MapPinned, '订单与轨迹跟进', '把关键进度放在容易找到的位置，方便及时沟通。', '进度概览'],
  [FileText, '账单与费用管理', '让费用信息更清楚，帮助团队做好日常核对。', '费用清单'],
] as const

const faqs = [
  ['适合哪些商户？', '适合有稳定跨境发货需求、希望让团队协作和发货安排更清晰的电商商户与企业团队。'],
  ['如何开始询价？', '从“立即获取报价”提交基本运输需求开始，我们会引导你完成下一步。'],
  ['能否多人协作？', '可以。ShipFlow 的设计重点之一，就是让团队成员围绕同一批发货事项更顺畅地沟通。'],
  ['如何查看运输进度？', '登录后可在工作台查看发货事项的重要进度与需要关注的提示。'],
] as const
</script>

<template>
  <div class="light-home">
    <HomeHero />

    <section id="services" ref="valueSection" class="light-capabilities home-section" :class="{ 'is-revealed': valuesRevealed }">
      <div class="container"><div class="light-heading light-heading--split"><div><span>WHY SHIPFLOW</span><h2>让跨境物流更省心，也更可控</h2></div><p>从发货到签收，为商户提供清晰、稳定、值得信赖的跨境物流体验。</p></div><div class="light-capabilities__grid"><article v-for="([icon, title, text], index) in values" :key="title" :style="{ '--delay': `${index * 85}ms` }"><span class="light-capabilities__glow" aria-hidden="true"></span><span class="light-capabilities__icon"><component :is="icon" :size="22" /></span><h3>{{ title }}</h3><p>{{ text }}</p></article></div></div>
    </section>

    <section ref="processSection" class="home-process home-section" :class="{ 'is-revealed': processRevealed }"><div class="container"><div class="home-section__intro"><span>HOW IT WORKS</span><h2>从询价开始，把发货安排得更明白</h2><p>不需要复杂准备。按自己的节奏提交需求、选择方案，再和团队一起持续跟进。</p></div><div class="home-process__grid"><article v-for="([no, icon, title, text], index) in steps" :key="title" :style="{ '--delay': `${index * 120}ms` }"><span class="home-process__number">{{ no }}</span><span class="home-process__icon"><component :is="icon" :size="21" /></span><div><h3>{{ title }}</h3><p>{{ text }}</p></div></article></div></div></section>

    <section ref="solutionsSection" class="home-solutions home-section" :class="{ 'is-revealed': solutionsRevealed }"><div class="container"><div class="home-section__intro home-section__intro--split"><div><span>FOR YOUR TEAM</span><h2>适配跨境商户日常发货的每个场景</h2></div><p>让不同角色围绕同一份发货安排配合，减少遗漏与反复沟通。</p></div><div class="home-solutions__grid"><article v-for="([icon, title, text, tag], index) in solutions" :key="title" :style="{ '--delay': `${index * 90}ms` }"><div class="home-solutions__visual"><span><component :is="icon" :size="23" /></span><i></i><b>{{ tag }}</b></div><h3>{{ title }}</h3><p>{{ text }}</p></article></div></div></section>

    <section ref="workspaceSection" class="home-workspace home-section" :class="{ 'is-revealed': workspaceRevealed }"><div class="container home-workspace__layout"><div><span class="light-kicker">ONE WORKSPACE</span><h2>一个工作台，掌握发货全程</h2><p>用更直观的方式查看待处理事项、运输中的安排和需要关注的信息，让团队沟通更顺畅。</p><ul><li><CheckCircle2 :size="17" /> 重要事项集中呈现</li><li><CheckCircle2 :size="17" /> 团队协作更有节奏</li><li><CheckCircle2 :size="17" /> 关键进度随时可见</li></ul><RouterLink class="text-link" to="/login">进入控制台 <span>→</span></RouterLink></div><div class="workspace-preview" aria-label="ShipFlow 工作台演示数据"><div class="workspace-preview__top"><div><small>SHIPFLOW WORKSPACE</small><b>本周发货概览</b></div><span>演示数据</span></div><div class="workspace-preview__stats"><div><Boxes :size="18" /><b>待处理事项</b><small>准备开始的发货安排</small></div><div><Truck :size="18" /><b>运送中</b><small>正在持续跟进</small></div><div><BellRing :size="18" /><b>需要关注</b><small>建议及时查看</small></div></div><div class="workspace-preview__summary"><div><span>本周概览</span><b>发货安排更清楚，团队跟进更顺畅</b></div><i><span></span><span></span><span></span><span></span><span></span></i></div><small class="workspace-preview__note">所有内容均为界面演示，不代表真实业务数据</small></div></div></section>

    <GlobalNetwork />

    <section ref="trustSection" class="home-trust home-section" :class="{ 'is-revealed': trustRevealed }"><div class="container"><div class="home-trust__heading"><span>WHY TEAMS CHOOSE SHIPFLOW</span><h2>为商户留出更多从容，<br />也让协作更有把握</h2><p>我们关注跨境发货里真正影响体验的事：信息是否清楚、沟通是否顺畅、问题是否有人及时响应。</p></div><div class="home-trust__points"><article><b>清晰</b><span>服务选择与进度信息更容易理解</span></article><article><b>稳定</b><span>日常发货安排有更连贯的跟进方式</span></article><article><b>协同</b><span>团队成员围绕同一事项更好配合</span></article></div><div class="faq"><h3>常见问题</h3><details v-for="([question, answer]) in faqs" :key="question"><summary>{{ question }}<span>+</span></summary><p>{{ answer }}</p></details></div></div></section>

    <section class="light-cta"><div class="container"><div><span>READY TO SHIP SMARTER</span><h2>从下一次发货开始，<br />让跨境物流更从容</h2></div><div><RouterLink class="glass-btn glass-btn--primary" to="/quote">在线预估运费</RouterLink><RouterLink class="glass-btn glass-btn--secondary" to="/apply">成为商户，获取正式报价</RouterLink><RouterLink class="glass-btn glass-btn--secondary" to="/login">登录控制台</RouterLink></div></div></section>
    <footer id="about" class="light-footer"><div class="container light-footer__main"><div><BrandMark /><p>ShipFlow 为跨境电商商户与企业团队提供清晰、稳定的发货协同体验，帮助团队更从容地安排跨境运输。</p></div><div class="light-footer__links"><RouterLink to="/quote">在线预估</RouterLink><RouterLink to="/apply">商户入驻</RouterLink><RouterLink to="/login">登录控制台</RouterLink><a href="#services">服务能力</a><a href="#channels">物流渠道</a></div></div><div class="container light-footer__bottom"><span>© 2026 ShipFlow</span><span>跨境物流服务体验平台</span></div></footer>
  </div>
</template>
