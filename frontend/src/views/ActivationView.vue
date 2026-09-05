<script setup lang="ts">
import { reactive, ref } from 'vue'
import { RouterLink } from 'vue-router'
import { CheckCircle2 } from '@lucide/vue'
import ActionError from '@/components/ActionError.vue'
import * as onboarding from '@/services/onboarding'
import { useSubmit } from '@/composables/useSubmit'

const form = reactive<onboarding.ActivationRequest>({ invitationToken: '', password: '' })
const submit = useSubmit()
const result = ref<onboarding.ActivationResponse>()

async function activate() {
  await submit.submit(async () => {
    if (!form.invitationToken.trim()) throw new Error('请输入邀请链接中的激活令牌。')
    if (form.password.length < 12) throw new Error('登录密码至少需要 12 个字符。')
    result.value = await onboarding.activateOnboardingAdministrator({ invitationToken: form.invitationToken.trim(), password: form.password })
  })
}
</script>

<template>
  <main class="public-page activation-page">
    <section class="panel activation-card">
      <template v-if="result">
        <CheckCircle2 :size="42" aria-hidden="true" />
        <h1>账号已激活</h1>
        <p>请使用已激活的商户账号登录 ShipFlow，开始管理跨境物流。</p>
        <RouterLink class="btn btn--primary" to="/login">前往登录</RouterLink>
      </template>
      <template v-else>
        <span class="kicker">商户账号激活</span>
        <h1>设置登录密码</h1>
        <p>请粘贴平台审核后安全发送给你的激活令牌，并设置一个不少于 12 位的登录密码。</p>
        <form class="compact-form" @submit.prevent="activate">
          <label><span>激活令牌</span><input v-model.trim="form.invitationToken" autocomplete="one-time-code" required /></label>
          <label><span>登录密码</span><input v-model="form.password" type="password" autocomplete="new-password" minlength="12" required /></label>
          <ActionError :message="submit.errorMessage.value" :code="submit.errorCode.value" :trace-id="submit.errorTraceId.value" />
          <button class="btn btn--primary" type="submit" :disabled="submit.submitting.value">{{ submit.submitting.value ? '提交中…' : '激活账号' }}</button>
        </form>
      </template>
    </section>
  </main>
</template>
