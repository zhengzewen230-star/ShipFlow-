<script setup lang="ts">
import { computed, onMounted, ref } from 'vue'
import { RefreshCw } from '@lucide/vue'
import DataState from '@/components/DataState.vue'
import { getApiErrorMessage, toApiError } from '@/services/http'
import * as onboarding from '@/services/onboarding'

const leads = ref<onboarding.GuestEstimateLead[]>([])
const selected = ref<onboarding.GuestEstimateLead>()
const loading = ref(false)
const saving = ref(false)
const error = ref('')
const errorTraceId = ref<string>()
const page = ref(1)
const totalPages = ref(0)
const filters = ref<{ status: '' | onboarding.GuestEstimateLeadStatus; keyword: string; from: string; to: string }>({ status: '', keyword: '', from: '', to: '' })
const remark = ref('')
const nextStatuses = computed(() => {
  if (!selected.value) return [] as onboarding.UpdateGuestEstimateLeadStatusRequest['status'][]
  return ({ RECEIVED: ['CONTACTING', 'CLOSED'], CONTACTING: ['QUALIFIED', 'CLOSED'], QUALIFIED: ['CLOSED'], CLOSED: [] } as const)[selected.value.status]
})
const labels: Record<onboarding.GuestEstimateLeadStatus, string> = { RECEIVED: '已收到', CONTACTING: '跟进中', QUALIFIED: '需求已确认', CLOSED: '已关闭' }
const modeLabels: Record<onboarding.TransportMode, string> = { OCEAN: '海运', AIR: '空运', ROAD: '陆运', RAIL: '铁路', COURIER: '快递' }

function date(value?: string) { if (!value) return '-'; const parsed = new Date(value); return Number.isNaN(parsed.getTime()) ? '-' : parsed.toLocaleString('zh-CN') }
function maskPhone(value: string) { return value.length <= 7 ? '***' : `${value.slice(0, 3)}****${value.slice(-4)}` }
function query() { return { page: page.value, pageSize: 20, status: filters.value.status || undefined, keyword: filters.value.keyword.trim() || undefined, from: filters.value.from ? new Date(filters.value.from).toISOString() : undefined, to: filters.value.to ? new Date(filters.value.to).toISOString() : undefined } }
async function load(reset = false) {
  if (reset) page.value = 1
  loading.value = true; error.value = ''; errorTraceId.value = undefined
  try { const result = await onboarding.listGuestEstimateLeads(query()); leads.value = result.items; totalPages.value = result.totalPages; if (selected.value) selected.value = result.items.find(item => item.id === selected.value?.id) ?? selected.value }
  catch (cause) { const apiError = toApiError(cause); error.value = getApiErrorMessage(apiError, '访客预估线索加载失败。'); errorTraceId.value = apiError.traceId }
  finally { loading.value = false }
}
async function select(lead: onboarding.GuestEstimateLead) { try { selected.value = await onboarding.getGuestEstimateLead(lead.id); remark.value = selected.value.handlingRemark || '' } catch (cause) { const apiError = toApiError(cause); error.value = getApiErrorMessage(apiError, '线索详情加载失败。'); errorTraceId.value = apiError.traceId } }
async function updateStatus(status: onboarding.UpdateGuestEstimateLeadStatusRequest['status']) {
  if (!selected.value) return
  saving.value = true; error.value = ''
  try { selected.value = await onboarding.updateGuestEstimateLeadStatus(selected.value.id, { status, handlingRemark: remark.value.trim() || undefined, version: selected.value.version }); await load() }
  catch (cause) { const apiError = toApiError(cause); error.value = getApiErrorMessage(apiError, '线索状态更新失败，请刷新后重试。'); errorTraceId.value = apiError.traceId }
  finally { saving.value = false }
}
onMounted(() => load())
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="kicker">平台运营</span><h1>访客预估线索</h1><p>访客提交的是运输需求线索，平台可联系、跟进和关闭；不会创建正式报价、订单或租户。</p></div><button class="btn btn--secondary" type="button" :disabled="loading" @click="load()"><RefreshCw :size="16" />刷新</button></div>
    <form class="lookup-bar" @submit.prevent="load(true)"><label>状态<select v-model="filters.status"><option value="">全部状态</option><option v-for="(label, status) in labels" :key="status" :value="status">{{ label }}</option></select></label><label>关键词<input v-model="filters.keyword" maxlength="128" placeholder="编号、联系人、邮箱或电话" /></label><label>开始时间<input v-model="filters.from" type="datetime-local" /></label><label>结束时间<input v-model="filters.to" type="datetime-local" /></label><button class="btn btn--primary" type="submit">查询</button></form>
    <div class="panel table-panel"><DataState :loading="loading" :error="error" :trace-id="errorTraceId" :retry="load" :empty="!leads.length" empty-title="暂无访客预估线索"><div class="data-table-wrap"><table class="data-table"><thead><tr><th>参考编号</th><th>运输方向</th><th>运输方式</th><th>货物</th><th>联系人</th><th>联系电话</th><th>状态</th><th>提交时间</th><th>操作</th></tr></thead><tbody><tr v-for="lead in leads" :key="lead.id"><td>{{ lead.referenceNo }}</td><td>{{ lead.originCountry }} → {{ lead.destinationCountry }}</td><td>{{ modeLabels[lead.transportMode] }}</td><td>{{ lead.cargoName }}</td><td>{{ lead.contactName }}</td><td>{{ maskPhone(lead.contactPhone) }}</td><td>{{ labels[lead.status] }}</td><td>{{ date(lead.createdAt) }}</td><td><button class="table-link" type="button" :disabled="saving" @click="select(lead)">查看详情</button></td></tr></tbody></table></div></DataState><div v-if="totalPages > 1" class="page-actions"><button class="btn btn--secondary" type="button" :disabled="page <= 1 || loading" @click="page--; load()">上一页</button><span>第 {{ page }} / {{ totalPages }} 页</span><button class="btn btn--secondary" type="button" :disabled="page >= totalPages || loading" @click="page++; load()">下一页</button></div></div>
    <div v-if="selected" class="panel quote-detail"><div class="quote-detail__hero"><div><span class="kicker">线索详情</span><h2>{{ selected.referenceNo }}</h2><p>{{ selected.originCountry }} → {{ selected.destinationCountry }} · {{ modeLabels[selected.transportMode] }}</p></div><span class="status-badge">{{ labels[selected.status] }}</span></div><div class="quote-detail__groups"><article class="quote-detail__group"><h2>货物与运输需求</h2><dl><div><dt>货物名称</dt><dd>{{ selected.cargoName }}</dd></div><div><dt>货物类型</dt><dd>{{ selected.cargoType }}</dd></div><div><dt>重量</dt><dd>{{ selected.weight }} kg</dd></div><div><dt>体积</dt><dd>{{ selected.volume }} m³</dd></div></dl></article><article class="quote-detail__group"><h2>联系信息</h2><dl><div><dt>联系人</dt><dd>{{ selected.contactName }}</dd></div><div><dt>企业邮箱</dt><dd>{{ selected.businessEmail }}</dd></div><div><dt>联系电话</dt><dd>{{ selected.contactPhone }}</dd></div><div><dt>提交时间</dt><dd>{{ date(selected.createdAt) }}</dd></div></dl></article></div><div v-if="nextStatuses.length" class="action-panel"><label>跟进备注<textarea v-model.trim="remark" maxlength="500" rows="2" placeholder="记录本次跟进结果"></textarea></label><div class="page-actions"><button v-for="status in nextStatuses" :key="status" class="btn btn--primary" type="button" :disabled="saving" @click="updateStatus(status)">标记为{{ labels[status] }}</button></div></div><p v-else class="data-state">该线索已关闭，不能再次变更状态。</p></div>
  </section>
</template>
