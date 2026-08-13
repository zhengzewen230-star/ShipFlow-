<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Eye, EyeOff, LockKeyhole, ShieldCheck } from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import { getApiErrorMessage } from '@/services/http'
import BrandMark from '@/components/BrandMark.vue'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const showPassword = ref(false)
const errorMessage = ref('')
const form = reactive({ tenantCode: '', username: '', password: '' })

async function submit() {
  errorMessage.value = ''
  try {
    await auth.login({ username: form.username.trim(), password: form.password, tenantCode: form.tenantCode.trim() || undefined })
    const redirect = typeof route.query.redirect === 'string' && (route.query.redirect === '/quote' || route.query.redirect.startsWith('/app')) ? route.query.redirect : '/app'
    await router.replace(redirect)
  } catch (error) {
    errorMessage.value = error instanceof Error && error.message === 'CSRF_COOKIE_MISSING'
      ? '未收到 CSRF Cookie，请确认通过 Nginx 同源访问。'
      : getApiErrorMessage(error, '登录失败，请检查账号信息或稍后重试。')
  }
}
</script>

<template>
  <main class="login-page">
    <section class="login-story">
      <RouterLink class="login-back" to="/"><ArrowLeft :size="17" /> 返回官网</RouterLink>
      <div class="login-story__content"><BrandMark /><span class="kicker kicker--dark">ShipFlow Console</span><h1>跨境履约的每一步，<br />都在同一个工作台</h1><p>连接报价、订单、仓库、轨迹与对账，让运营团队围绕同一份信息协作。</p><div class="login-story__proof"><ShieldCheck :size="22" /><span><b>安全访问</b><small>账号权限清晰 · 会话安全保护</small></span></div></div>
      <div class="login-story__pattern"></div>
    </section>
    <section class="login-panel">
      <form class="login-form" @submit.prevent="submit">
        <div><span class="login-form__eyebrow">欢迎回来</span><h2>登录业务控制台</h2><p>平台用户可留空租户编码；租户用户请填写所属租户。</p></div>
        <div v-if="errorMessage" class="alert alert--error" role="alert">{{ errorMessage }}</div>
        <label><span>租户编码 <small>选填</small></span><input v-model="form.tenantCode" autocomplete="organization" placeholder="例如：merchant-cn" /></label>
        <label><span>用户名</span><input v-model="form.username" autocomplete="username" required placeholder="请输入用户名" /></label>
        <label><span>密码</span><div class="password-input"><input v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" required placeholder="请输入密码" /><button type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="18" /><Eye v-else :size="18" /></button></div></label>
        <button class="btn btn--primary btn--large login-submit" type="submit" :disabled="auth.loading"><LockKeyhole v-if="auth.loading" :size="18" />{{ auth.loading ? '安全登录中…' : '登录控制台' }}<ArrowRight v-if="!auth.loading" :size="18" /></button>
        <p class="login-form__hint">为保护账号安全，请仅在可信设备上登录，离开时及时退出。</p>
      </form>
    </section>
  </main>
</template>
