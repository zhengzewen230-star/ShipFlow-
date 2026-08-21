<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { ArrowLeft, ArrowRight, Check, Info, LoaderCircle, LockKeyhole } from '@lucide/vue'
import { useRoute, useRouter } from 'vue-router'
import type { CreateQuoteRequest, Quote } from '@/services/quotes'
import type { GuestCargoType, GuestEstimateRequest, GuestEstimateResponse, TransportMode } from '@/services/onboarding'
import QuoteStepper from '@/components/QuoteStepper.vue'
import { useSubmit } from '@/composables/useSubmit'
import { getApiErrorMessage, toApiError } from '@/services/http'
import { useAuthStore } from '@/stores/auth'
import * as logistics from '@/services/logistics'
import * as quotes from '@/services/quotes'
import * as stores from '@/services/stores'
import * as onboarding from '@/services/onboarding'
import { quoteReuseInput } from './quoteReuse'

const steps = ['选择店铺与渠道', '填写尺寸重量', '确认并创建报价']
const router = useRouter()
const route = useRoute()
const auth = useAuthStore()
const current = ref(0)
const loadingOptions = ref(false)
const optionsError = ref('')
const optionState = ref<'ready' | 'stores-empty' | 'channels-empty' | 'forbidden'>('ready')
const errors = ref<Record<string, string>>({})
const availableStores = ref<stores.Store[]>([])
const availableChannels = ref<logistics.PublicLogisticsChannel[]>([])
const createdQuote = ref<Quote>()
const quoteReuseNotice = ref('')
const quoteReuseError = ref('')
const quoteReuseLoading = ref(false)
const submit = useSubmit()
const estimateSubmit = useSubmit()
const guestEstimate = ref<GuestEstimateResponse>()
const guestErrors = ref<Record<string, string>>({})
const guestForm = reactive<{ originCountry: string; destinationCountry: string; transportMode: TransportMode | ''; cargoType: GuestCargoType | ''; cargoName: string; weight: string; volume: string; contactName: string; businessEmail: string; contactPhone: string }>({ originCountry: '', destinationCountry: '', transportMode: '', cargoType: '', cargoName: '', weight: '', volume: '', contactName: '', businessEmail: '', contactPhone: '' })
const guestCountries = [
  { code: 'CN', label: '中国（CN）' }, { code: 'US', label: '美国（US）' }, { code: 'CA', label: '加拿大（CA）' }, { code: 'AU', label: '澳大利亚（AU）' },
  { code: 'JP', label: '日本（JP）' }, { code: 'KR', label: '韩国（KR）' }, { code: 'GB', label: '英国（GB）' }, { code: 'DE', label: '德国（DE）' },
]
const guestCargoTypes: { value: GuestCargoType; label: string }[] = [
  { value: 'GENERAL', label: '普货' }, { value: 'BATTERY', label: '带电产品' }, { value: 'SENSITIVE', label: '敏感货' }, { value: 'LIQUID_POWDER', label: '液体/粉末' }, { value: 'FRAGILE', label: '易碎品' }, { value: 'OVERSIZED', label: '超大件' }, { value: 'OTHER', label: '其他' },
]
const form = reactive({ storeId: '', channelId: '', destinationCountry: '', length: '', width: '', height: '', weight: '' })
const isConsoleQuote = computed(() => route.path.startsWith('/app/'))

const countries = computed(() => [...new Set(availableChannels.value.flatMap(channel => channel.serviceCountries))].sort())
const selectedChannel = computed(() => availableChannels.value.find(channel => String(channel.id) === form.channelId))
const apiRequest = computed<CreateQuoteRequest>(() => ({
  storeId: Number(form.storeId),
  channelId: Number(form.channelId),
  declaredWeight: measurement(form.weight),
  declaredLength: measurement(form.length),
  declaredWidth: measurement(form.width),
  declaredHeight: measurement(form.height),
  destinationCountry: form.destinationCountry,
}))

function measurement(value: string) {
  const parsed = Number(value)
  return /^\d{1,15}(?:\.\d{1,3})?$/.test(value.trim()) && Number.isFinite(parsed) && parsed > 0 ? parsed : 0
}

function optionLoadError(source: string, reason: unknown) {
  const error = toApiError(reason)
  if (!error.status || error.status >= 500) {
    const traceSuffix = error.traceId ? `（追踪编号：${error.traceId}）` : ''
    return `${source}服务暂时不可用，请稍后重试。${traceSuffix}`
  }
  return getApiErrorMessage(reason, `${source}加载失败，请稍后重试。`)
}

async function loadOptions(countryCode?: string) {
  loadingOptions.value = true
  optionsError.value = ''
  optionState.value = 'ready'
  try {
    const [storeResult, channelResult] = await Promise.allSettled([
      stores.listStores({ status: 'ACTIVE' }),
      logistics.listAvailableLogisticsChannels({ serviceCountry: countryCode, status: 'ACTIVE' }),
    ])
    const failed = [
      { source: '店铺', result: storeResult },
      { source: '物流渠道', result: channelResult },
    ].find((item): item is { source: string; result: PromiseRejectedResult } => item.result.status === 'rejected')
    if (failed) {
      const error = toApiError(failed.result.reason)
      if (error.status === 401) {
        await router.replace({ name: 'login', query: { redirect: '/quote' } })
        return
      }
      if (error.status === 403) {
        optionState.value = 'forbidden'
        return
      }
      optionsError.value = optionLoadError(failed.source, failed.result.reason)
    }
    if (storeResult.status !== 'fulfilled' || channelResult.status !== 'fulfilled') return
    availableStores.value = storeResult.value.items
    availableChannels.value = channelResult.value.items
    if (!availableStores.value.some(store => String(store.id) === form.storeId)) form.storeId = ''
    if (!availableChannels.value.some(channel => String(channel.id) === form.channelId)) form.channelId = ''
    if (!availableStores.value.length) optionState.value = 'stores-empty'
    else if (!availableChannels.value.length) optionState.value = 'channels-empty'
  } finally {
    loadingOptions.value = false
  }
}

async function initialize() {
  if (!auth.initialized) await auth.restoreSession()
  if (!auth.isAuthenticated || auth.scope !== 'TENANT') return
  await loadOptions()
  await loadQuoteReuse()
}

async function loadQuoteReuse() {
  const copyQuoteId = typeof route.query.copyQuoteId === 'string' ? route.query.copyQuoteId : ''
  if (!/^\d+$/.test(copyQuoteId) || Number(copyQuoteId) <= 0) return
  quoteReuseLoading.value = true
  quoteReuseError.value = ''
  try {
    const quote = await quotes.getQuote(copyQuoteId)
    Object.assign(form, quoteReuseInput(quote))
    quoteReuseNotice.value = route.query.mode === 'requote'
      ? '已带入原报价的业务输入。重新报价将按当前有效规则重新计算金额、有效期和规则版本。'
      : '已带入原报价的业务输入。复制报价将按当前有效规则重新计算金额、有效期和规则版本。'
  } catch (cause) {
    quoteReuseError.value = getApiErrorMessage(cause, '无法读取可复用的报价输入。')
  } finally {
    quoteReuseLoading.value = false
  }
}

watch(() => form.destinationCountry, async country => {
  if (auth.isAuthenticated && auth.scope === 'TENANT') await loadOptions(country || undefined)
})

watch(() => form.storeId, async storeId => {
  if (storeId && auth.isAuthenticated && auth.scope === 'TENANT') await loadOptions(form.destinationCountry || undefined)
})

function validateStep() {
  const nextErrors: Record<string, string> = {}
  if (current.value === 0) {
    if (!form.storeId) nextErrors.storeId = '请选择店铺'
    if (!form.destinationCountry) nextErrors.destinationCountry = '请选择目的国家或地区'
    if (!form.channelId) nextErrors.channelId = '请选择可用物流渠道'
  }
  if (current.value === 1) {
    for (const key of ['length', 'width', 'height', 'weight'] as const) if (measurement(form[key]) <= 0) nextErrors[key] = '请输入大于 0 的数值'
  }
  errors.value = nextErrors
  return Object.keys(nextErrors).length === 0
}

function next() {
  if (!validateStep()) return
  if (current.value < steps.length - 1) current.value += 1
}

async function createQuote() {
  if (!validateStep()) return
  await submit.submit(async () => {
    createdQuote.value = await quotes.createQuote(apiRequest.value)
  })
}

function loginAndContinue() {
  router.push({ name: 'login', query: { redirect: '/app/quotes/create' } })
}

async function createGuestEstimate() {
  const fields = ['originCountry', 'destinationCountry', 'transportMode', 'cargoType', 'cargoName', 'weight', 'volume', 'contactName', 'businessEmail', 'contactPhone'] as const
  guestErrors.value = Object.fromEntries(fields.filter(field => !guestForm[field]).map(field => [field, '请填写此项']))
  if (guestForm.originCountry === guestForm.destinationCountry) guestErrors.value.destinationCountry = '起运国家与目的国家不能相同'
  if (guestForm.cargoName.trim().length < 2 || guestForm.cargoName.trim().length > 80) guestErrors.value.cargoName = '货物名称需为 2–80 个字符'
  if (!/^\S+@\S+\.\S+$/.test(guestForm.businessEmail)) guestErrors.value.businessEmail = '请输入企业邮箱'
  if (Number(guestForm.weight) <= 0) guestErrors.value.weight = '请输入大于 0 的重量'
  if (Number(guestForm.volume) <= 0) guestErrors.value.volume = '请输入大于 0 的体积'
  if (Object.keys(guestErrors.value).length) return
  await estimateSubmit.submit(async () => {
    guestEstimate.value = await onboarding.submitGuestEstimate({ ...guestForm, transportMode: guestForm.transportMode as TransportMode, cargoType: guestForm.cargoType as GuestCargoType, cargoName: guestForm.cargoName.trim(), weight: Number(guestForm.weight), volume: Number(guestForm.volume) } as GuestEstimateRequest)
  })
}

onMounted(initialize)
</script>

<template>
  <section class="quote-page">
    <div class="container quote-page__header"><span class="kicker">{{ isConsoleQuote ? '正式报价' : '在线报价' }}</span><h1>{{ isConsoleQuote ? '创建正式报价' : '预估运费与正式报价，分两步开始' }}</h1><p>{{ isConsoleQuote ? '基于当前租户的店铺与可用物流渠道创建正式报价。' : '访客可先提交运输需求获取人工预估；正式报价需完成商户审核、登录并配置店铺与渠道后创建。' }}</p></div>
    <div class="container quote-shell">
      <div v-if="!auth.initialized" class="quote-success"><LoaderCircle class="spin" :size="30" /><h2>正在恢复登录状态</h2></div>
      <div v-else-if="!auth.isAuthenticated" class="guest-estimate-shell">
        <div v-if="guestEstimate" class="quote-success quote-success--result quote-success--guest">
          <span><Check :size="30" /></span>
          <h2>预估需求已提交</h2>
          <p>{{ guestEstimate.notice }}</p>
          <button class="btn btn--primary" type="button" @click="loginAndContinue">登录后创建正式报价 <ArrowRight :size="17" /></button>
        </div>
        <template v-else>
          <aside class="guest-estimate-intro"><span class="kicker">访客预估运费</span><h2>先描述运输需求，<br />再获取预估</h2><ol><li><b>1</b><div><strong>填写运输信息</strong><span>用基本货物与运输信息开始咨询。</span></div></li><li><b>2</b><div><strong>获取人工预估</strong><span>我们会根据需求与您确认可选方案。</span></div></li><li><b>3</b><div><strong>入驻后创建正式报价</strong><span>商户账号可继续完成正式发货安排。</span></div></li></ol><div class="guest-estimate-tags"><span>跨境电商备货</span><span>海外仓补货</span><span>样品寄送</span><span>多渠道发货</span></div><p class="guest-estimate-privacy">仅用于联系与运输预估，不创建订单、不展示内部价格。</p><button class="guest-estimate-login" type="button" @click="loginAndContinue">已有商户账号？<b>登录后创建正式报价</b></button></aside>
          <form class="guest-estimate-form" @submit.prevent="createGuestEstimate"><div class="form-heading"><span>填写预估信息</span><h2>获取预估运费</h2></div><div class="form-grid"><label><span>起运国家 / 地区</span><select v-model="guestForm.originCountry"><option value="">请选择</option><option v-for="country in guestCountries" :key="country.code" :value="country.code">{{ country.label }}</option></select><small v-if="guestErrors.originCountry" class="field-error">{{ guestErrors.originCountry }}</small></label><label><span>目的国家 / 地区</span><select v-model="guestForm.destinationCountry"><option value="">请选择</option><option v-for="country in guestCountries" :key="country.code" :value="country.code" :disabled="country.code === guestForm.originCountry">{{ country.label }}</option></select><small v-if="guestErrors.destinationCountry" class="field-error">{{ guestErrors.destinationCountry }}</small></label><label><span>运输方式</span><select v-model="guestForm.transportMode"><option value="">请选择</option><option value="OCEAN">海运</option><option value="AIR">空运</option><option value="ROAD">陆运</option><option value="RAIL">铁路</option><option value="COURIER">快递</option></select><small v-if="guestErrors.transportMode" class="field-error">{{ guestErrors.transportMode }}</small></label><label><span>货物类型</span><select v-model="guestForm.cargoType"><option value="">请选择</option><option v-for="cargo in guestCargoTypes" :key="cargo.value" :value="cargo.value">{{ cargo.label }}</option></select><small v-if="guestErrors.cargoType" class="field-error">{{ guestErrors.cargoType }}</small></label><label class="form-grid__full"><span>货物名称</span><input v-model.trim="guestForm.cargoName" maxlength="80" placeholder="例如：蓝牙耳机、家居收纳箱" /><small v-if="guestErrors.cargoName" class="field-error">{{ guestErrors.cargoName }}</small></label><label><span>重量（kg）</span><input v-model="guestForm.weight" inputmode="decimal" /><small v-if="guestErrors.weight" class="field-error">{{ guestErrors.weight }}</small></label><label><span>体积（m³）</span><input v-model="guestForm.volume" inputmode="decimal" /><small v-if="guestErrors.volume" class="field-error">{{ guestErrors.volume }}</small></label><label><span>联系人</span><input v-model.trim="guestForm.contactName" /><small v-if="guestErrors.contactName" class="field-error">{{ guestErrors.contactName }}</small></label><label><span>企业邮箱</span><input v-model.trim="guestForm.businessEmail" type="email" /><small v-if="guestErrors.businessEmail" class="field-error">{{ guestErrors.businessEmail }}</small></label><label class="form-grid__full"><span>手机号 / WhatsApp</span><input v-model.trim="guestForm.contactPhone" /><small v-if="guestErrors.contactPhone" class="field-error">{{ guestErrors.contactPhone }}</small></label><div class="form-actions form-grid__full"><RouterLink class="btn btn--ghost" to="/apply">申请商户入驻</RouterLink><button class="btn btn--primary" :disabled="estimateSubmit.submitting.value">{{ estimateSubmit.submitting.value ? '提交中…' : '获取预估运费' }}</button></div><div v-if="estimateSubmit.errorMessage.value" class="alert alert--error form-grid__full">{{ estimateSubmit.errorMessage.value }}</div></div></form>
        </template>
      </div>
      <div v-else-if="auth.scope !== 'TENANT'" class="quote-success"><span><Info :size="30" /></span><h2>当前账号不能创建租户报价</h2><p>请使用具备租户身份和报价权限的账号登录后继续。</p></div>
      <div v-else-if="optionState === 'forbidden'" class="quote-success"><span><LockKeyhole :size="30" /></span><h2>当前账号暂无创建报价所需权限，请联系租户管理员</h2><p>报价创建需要租户身份，以及读取当前租户店铺和可用物流渠道的权限。报价表单已禁用。</p></div>
      <div v-else-if="optionState === 'stores-empty'" class="quote-success"><span><Info :size="30" /></span><h2>请先配置店铺</h2><p>当前租户没有可用于正式报价的已启用店铺。请联系租户管理员完成店铺配置后再试。</p></div>
      <div v-else-if="optionState === 'channels-empty'" class="quote-success"><span><Info :size="30" /></span><h2>暂无可用物流渠道</h2><p>当前没有已启用的物流渠道可用于正式报价。请联系平台管理员确认渠道、服务国家和价格规则配置。</p></div>
      <template v-else-if="createdQuote">
        <div class="quote-success quote-success--result"><span><Check :size="30" /></span><h2>正式报价已创建</h2><p>报价已保存到当前租户的工作台，可继续查看或创建订单。</p><div class="quote-result__actions"><RouterLink class="btn btn--secondary" :to="`/app/quotes?quoteId=${createdQuote.id}`">查看报价</RouterLink><RouterLink class="btn btn--primary" :to="`/app/orders?quoteId=${createdQuote.id}`">创建订单 <ArrowRight :size="17" /></RouterLink></div></div>
      </template>
      <template v-else>
        <QuoteStepper :steps="steps" :current="current" />
        <form class="quote-form" @submit.prevent="current === steps.length - 1 ? createQuote() : next()">
          <div class="quote-form__main">
            <div v-if="optionsError" class="alert alert--error" role="alert">{{ optionsError }}</div>
            <div v-if="quoteReuseLoading" class="data-state">正在读取原报价输入...</div>
            <div v-if="quoteReuseNotice" class="alert alert--warning" role="status">{{ quoteReuseNotice }}</div>
            <div v-if="quoteReuseError" class="alert alert--error" role="alert">{{ quoteReuseError }}</div>
            <div class="form-heading"><span>步骤 {{ current + 1 }} / {{ steps.length }}</span><h2>{{ steps[current] }}</h2></div>
            <div v-if="current === 0" class="form-grid">
              <label><span>店铺</span><select v-model="form.storeId" :disabled="loadingOptions" :class="{ invalid: errors.storeId }"><option value="">请选择店铺</option><option v-for="store in availableStores" :key="store.id" :value="String(store.id)">{{ store.storeName }}（{{ store.platformCode }}）</option></select><small v-if="errors.storeId" class="field-error">{{ errors.storeId }}</small></label>
              <label><span>目的国家 / 地区</span><select v-model="form.destinationCountry" :disabled="loadingOptions" :class="{ invalid: errors.destinationCountry }"><option value="">请选择</option><option v-for="country in countries" :key="country" :value="country">{{ country }}</option></select><small v-if="errors.destinationCountry" class="field-error">{{ errors.destinationCountry }}</small></label>
              <label class="form-grid__full"><span>可用物流渠道</span><select v-model="form.channelId" :disabled="loadingOptions || !form.destinationCountry" :class="{ invalid: errors.channelId }"><option value="">{{ form.destinationCountry ? '请选择渠道' : '请先选择目的国家或地区' }}</option><option v-for="channel in availableChannels" :key="channel.id" :value="String(channel.id)">{{ channel.channelName }} · {{ channel.transportMode }}</option></select><small v-if="errors.channelId" class="field-error">{{ errors.channelId }}</small></label>
              <div class="form-note form-grid__full"><Info :size="17" /><span>店铺和渠道来自当前租户可用数据；选择目的国家后会按该国家重新加载可用渠道。</span></div>
            </div>
            <div v-if="current === 1" class="form-grid form-grid--four">
              <label v-for="field in (['length', 'width', 'height', 'weight'] as const)" :key="field"><span>{{ { length: '长度（cm）', width: '宽度（cm）', height: '高度（cm）', weight: '重量（kg）' }[field] }}</span><input v-model="form[field]" inputmode="decimal" :class="{ invalid: errors[field] }" placeholder="0.000" /><small v-if="errors[field]" class="field-error">{{ errors[field] }}</small></label>
              <div class="measure-preview form-grid__full"><span>申报体积</span><b>{{ ((Number(form.length) || 0) * (Number(form.width) || 0) * (Number(form.height) || 0) / 1_000_000).toFixed(3) }} m³</b><small>正式计费重量和金额由后端已发布价格规则计算。</small></div>
            </div>
            <div v-if="current === 2" class="form-grid">
              <div class="form-note form-grid__full"><Info :size="17" /><span>将提交店铺、渠道、目的国家和申报尺寸重量。起运地、货物信息、服务偏好及联系人不属于当前报价创建契约，因此不会提交或保存。</span></div>
              <div class="form-grid__full quote-confirm"><span>已选渠道</span><b>{{ selectedChannel ? `${selectedChannel.channelName} · ${selectedChannel.transportMode}` : '待选择' }}</b><span>申报参数</span><b>{{ form.weight }} kg · {{ form.length }} × {{ form.width }} × {{ form.height }} cm</b></div>
            </div>
            <div class="form-actions"><button v-if="current > 0" class="btn btn--ghost" type="button" @click="current -= 1"><ArrowLeft :size="17" /> 上一步</button><span></span><button class="btn btn--primary" type="submit" :disabled="loadingOptions || submit.submitting.value">{{ current === steps.length - 1 ? (submit.submitting.value ? '创建报价中…' : '创建正式报价') : '下一步' }} <ArrowRight v-if="current !== steps.length - 1" :size="17" /></button></div>
            <div v-if="submit.errorMessage.value" class="alert alert--error" role="alert">{{ submit.errorMessage.value }}</div>
          </div>
          <aside class="quote-summary"><span>报价摘要</span><dl><div><dt>店铺</dt><dd>{{ availableStores.find(store => String(store.id) === form.storeId)?.storeName || '待选择' }}</dd></div><div><dt>目的国家</dt><dd>{{ form.destinationCountry || '待选择' }}</dd></div><div><dt>渠道</dt><dd>{{ selectedChannel?.channelName || '待选择' }}</dd></div><div><dt>尺寸重量</dt><dd>{{ form.weight || '0' }} kg</dd></div></dl><p class="quote-summary__hint">确认运输信息后，即可创建正式报价。</p></aside>
        </form>
      </template>
    </div>
  </section>
</template>
