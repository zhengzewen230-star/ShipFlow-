<script setup lang="ts">
import { reactive, ref } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ArrowLeft, ArrowRight, Eye, EyeOff, LockKeyhole, ShieldCheck } from '@lucide/vue'
import { useAuthStore } from '@/stores/auth'
import { toApiError } from '@/services/http'
import BrandMark from '@/components/BrandMark.vue'
import ActionError from '@/components/ActionError.vue'
import { defaultConsolePath, isSafeConsoleRedirect } from '@/navigation/access'

const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const showPassword = ref(false)
const errorMessage = ref('')
const errorCode = ref<string>()
const errorTraceId = ref<string>()
const fieldErrors = reactive({ username: '', password: '' })
const form = reactive({ tenantCode: '', username: '', password: '' })

async function submit() {
  errorMessage.value = ''; errorCode.value = undefined; errorTraceId.value = undefined
  fieldErrors.username = form.username.trim() ? '' : '请输入用户名。'
  fieldErrors.password = form.password ? '' : '请输入密码。'
  if (fieldErrors.username || fieldErrors.password) return
  try {
    await auth.login({ username: form.username.trim(), password: form.password, tenantCode: form.tenantCode.trim() || undefined })
    const redirect = isSafeConsoleRedirect(route.query.redirect) ? route.query.redirect : defaultConsolePath(auth)
    await router.replace(redirect)
  } catch (error) {
    const converted = toApiError(error, '登录失败，请检查账号信息或稍后重试。')
    errorMessage.value = error instanceof Error && error.message === 'CSRF_COOKIE_MISSING' ? '未收到 CSRF Cookie，请确认通过 Nginx 同源访问。' : converted.message
    errorCode.value = converted.code
    errorTraceId.value = converted.traceId
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
      <form class="login-form" novalidate @submit.prevent="submit">
        <div class="login-form__intro"><span class="login-form__eyebrow">欢迎回来</span><h2>登录业务控制台</h2><p>平台用户可留空租户编码；租户用户请填写所属租户。</p></div>
        <div v-if="route.query.reason === 'session-expired'" class="alert alert--warning" role="status">登录状态已失效，原请求已终止。请重新登录后继续。</div>
        <ActionError v-if="errorMessage" :message="errorMessage" :code="errorCode" :trace-id="errorTraceId" />
        <label><span>租户编码 <small>选填</small></span><input v-model="form.tenantCode" autocomplete="organization" placeholder="例如：merchant-cn" /></label>
        <label><span>用户名</span><input v-model="form.username" autocomplete="username" required placeholder="请输入用户名" :aria-invalid="!!fieldErrors.username" aria-describedby="login-username-error" /><small id="login-username-error" class="field-error">{{ fieldErrors.username }}</small></label>
        <label><span>密码</span><div class="password-input"><input v-model="form.password" :type="showPassword ? 'text' : 'password'" autocomplete="current-password" required placeholder="请输入密码" :aria-invalid="!!fieldErrors.password" aria-describedby="login-password-error" /><button type="button" :aria-label="showPassword ? '隐藏密码' : '显示密码'" @click="showPassword = !showPassword"><EyeOff v-if="showPassword" :size="18" /><Eye v-else :size="18" /></button></div><small id="login-password-error" class="field-error">{{ fieldErrors.password }}</small></label>
        <button class="btn btn--primary btn--large login-submit" type="submit" :disabled="auth.loading"><LockKeyhole v-if="auth.loading" :size="18" />{{ auth.loading ? '安全登录中…' : '登录控制台' }}<ArrowRight v-if="!auth.loading" :size="18" /></button>
        <p class="login-form__hint">为保护账号安全，请仅在可信设备上登录，离开时及时退出。</p>
      </form>
    </section>
  </main>
</template>
