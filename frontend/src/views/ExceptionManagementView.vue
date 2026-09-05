<script setup lang="ts">
import { computed, onMounted, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import DataState from '@/components/DataState.vue'
import ActionError from '@/components/ActionError.vue'
import CopyTextButton from '@/components/CopyTextButton.vue'
import ListPagination from '@/components/ListPagination.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import FilterToolbar from '@/components/FilterToolbar.vue'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { useSubmit } from '@/composables/useSubmit'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notifications'
import { toApiError } from '@/services/http'
import * as api from '@/services/exceptions'
import type { ExceptionCase, ExceptionStatus, ResponsibleParty } from '@/services/exceptions'
import * as users from '@/services/users'
import type { Id } from '@/types/api'
import { allowedExceptionTransitions, canMutateException } from './exceptionWorkflow'
import { defaultExceptionFilter, parseExceptionQuery, toExceptionApiQuery, toExceptionRouteQuery, type ExceptionFilterState } from './exceptionQuery'
import { displayValue, formatMoney } from '@/utils/display'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const notifications = useNotificationStore()
const { confirm } = useConfirmAction()
const action = useSubmit()
const loading = ref(false)
const error = ref('')
const errorTraceId = ref<string>()
const errorCode = ref<string>()
const errorStatus = ref<number>()
const rows = ref<ExceptionCase[]>([])
const selected = ref<ExceptionCase>()
const total = ref(0)
const totalPages = ref(0)
const assignees = ref<users.User[]>([])
const assigneeError = ref('')
const selectedEvidenceIds = ref<Id[]>([])
const canManage = computed(() => auth.hasPermission('exception:manage'))
const mutable = computed(() => !!selected.value && canMutateException(selected.value.status))
const allowedStatuses = computed(() => selected.value ? allowedExceptionTransitions(selected.value.status) : [])
const regexCurrency = /^[A-Za-z]{3}$/
const claimReady = computed(() => !!selected.value && selected.value.status === 'PROCESSING' && !!form.claimReason.trim() && Number(form.claimAmount) > 0 && regexCurrency.test(form.currency) && selectedEvidenceIds.value.length > 0)

const filters = reactive<ExceptionFilterState>({ ...defaultExceptionFilter })
const form = reactive({ assignedToUserId: '', responsibleParty: 'PROVIDER' as ResponsibleParty, assignReason: '', targetStatus: '' as ExceptionStatus | '', statusReason: '', recordType: 'FOLLOW_UP' as api.HandlingRecord['recordType'], recordContent: '', evidenceDescription: '', claimAmount: '', currency: '', claimReason: '', claimActionReason: '', claimResult: 'APPROVED' as 'APPROVED' | 'PARTIALLY_APPROVED' | 'REJECTED', approvedAmount: '', resultReason: '', financeReason: '', closeReason: '' })
const evidenceFile = ref<File>()

function displayDate(value?: string | null) { return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' }).format(new Date(value)) + ' UTC' : '—' }
function displayMoney(value?: number | null, currency?: string) { return formatMoney(value, currency) }
function toDisplayDetail(detail: ExceptionCase): ExceptionCase {
  return {
    ...detail,
    exceptionType: displayValue('exceptionType', detail.exceptionType) as ExceptionCase['exceptionType'],
    responsibleParty: detail.responsibleParty ? displayValue('responsibleParty', detail.responsibleParty) as ResponsibleParty : null,
    timeline: detail.timeline?.map(event => ({
      ...event,
      statusBefore: event.statusBefore ? displayValue('status', event.statusBefore) : event.statusBefore,
      statusAfter: event.statusAfter ? displayValue('status', event.statusAfter) : event.statusAfter,
    })),
  }
}

async function load() {
  loading.value = true; error.value = ''; errorCode.value = undefined; errorStatus.value = undefined; errorTraceId.value = undefined
  try {
    const result = await api.listExceptionCases(toExceptionApiQuery(filters))
    // List rows are a presentation projection only. Detail/write flows keep raw API values.
    rows.value = result.items.map(item => ({
      ...item,
      exceptionType: displayValue('exceptionType', item.exceptionType) as ExceptionCase['exceptionType'],
      responsibleParty: item.responsibleParty ? displayValue('responsibleParty', item.responsibleParty) as ResponsibleParty : null,
    }))
    total.value = result.total; totalPages.value = result.totalPages
  } catch (cause) { const converted = toApiError(cause); error.value = converted.message; errorCode.value = converted.code; errorStatus.value = converted.status; errorTraceId.value = converted.traceId }
  finally { loading.value = false }
}

function syncEvidenceSelection() { selectedEvidenceIds.value = selected.value?.evidenceAttachments?.map(file => file.id) ?? [] }
async function loadSelected(id: Id) {
  loading.value = true; error.value = ''
  try {
    const detail = await api.getExceptionCase(id)
    selected.value = toDisplayDetail(detail)
    syncEvidenceSelection(); form.targetStatus = allowedExceptionTransitions(selected.value.status)[0] ?? ''
  }
  catch (cause) { const converted = toApiError(cause); error.value = converted.message; errorCode.value = converted.code; errorStatus.value = converted.status; errorTraceId.value = converted.traceId }
  finally { loading.value = false }
}
function selectCase(item: ExceptionCase) { void router.replace({ query: toExceptionRouteQuery(filters, String(item.id)) }) }

async function refreshSelected() { if (selected.value) { selected.value = toDisplayDetail(await api.getExceptionCase(selected.value.id)); syncEvidenceSelection() } }
async function loadAssignees() {
  assigneeError.value = ''
  try { assignees.value = (await users.listUsers({ status: 'ACTIVE', page: 1, pageSize: 100 })).items }
  catch (cause) { assigneeError.value = toApiError(cause).message }
}
async function run<T>(label: string, operation: () => Promise<T>): Promise<T | undefined> {
  const result = await action.submit(operation)
  if (result === undefined) return undefined
  notifications.push(`${label}成功`, 'success')
  try { await refreshSelected(); await load() }
  catch (cause) {
    const converted = toApiError(cause)
    error.value = `操作已成功，但详情刷新失败：${converted.message}`
    errorTraceId.value = converted.traceId
  }
  if (selected.value) form.targetStatus = allowedExceptionTransitions(selected.value.status)[0] ?? ''
  return result
}
function syncRoute(page = 1, pageSize = filters.pageSize) { filters.page = page; filters.pageSize = pageSize; void router.replace({ query: toExceptionRouteQuery(filters) }) }
function search() { syncRoute(1) }
function reset() { Object.assign(filters, defaultExceptionFilter); selected.value = undefined; void router.replace({ query: {} }) }
function chooseFile(event: Event) { evidenceFile.value = (event.target as HTMLInputElement).files?.[0] }

async function assign() { if (!selected.value || !form.assignedToUserId) return; await run('负责人分配', () => api.assignExceptionCase(selected.value!.id, { assignedToUserId: form.assignedToUserId, responsibleParty: form.responsibleParty, reason: form.assignReason, version: selected.value!.version })) }
async function transition() { if (!selected.value || !form.targetStatus) return; if (form.targetStatus === 'CLOSED' && !await confirm({ title: '确认关闭异常', description: '关闭后禁止继续分配、添加记录、上传证据或修改索赔，请确认所有条件已经满足。', confirmLabel: '关闭异常', danger: true })) return; await run(form.targetStatus === 'CLOSED' ? '异常关闭' : '状态更新', () => api.transitionExceptionCase(selected.value!.id, { status: form.targetStatus as Exclude<ExceptionStatus, 'OPEN'>, reason: form.statusReason, version: selected.value!.version })) }
async function addRecord() { if (!selected.value || !form.recordContent.trim()) return; await run('处理记录添加', () => api.addHandlingRecord(selected.value!.id, { recordType: form.recordType, content: form.recordContent.trim(), version: selected.value!.version })); form.recordContent = '' }
async function upload() { if (!selected.value || !evidenceFile.value) return; if (evidenceFile.value.size > 10 * 1024 * 1024) { notifications.push('附件不得超过 10 MiB', 'error'); return } const uploaded = await run('证据上传', () => api.uploadEvidence(selected.value!.id, evidenceFile.value!, selected.value!.version, form.evidenceDescription)); if (uploaded) selectedEvidenceIds.value = [...new Set([...selectedEvidenceIds.value, uploaded.id])]; evidenceFile.value = undefined; form.evidenceDescription = '' }
async function createClaim() { if (!selected.value) return; if (selected.value.status !== 'PROCESSING') { notifications.push('请先将异常推进到 PROCESSING 后再发起索赔', 'error'); return } const claim = await run('索赔发起', () => api.createClaim(selected.value!.id, { claimAmount: Number(form.claimAmount), currency: form.currency.toUpperCase(), claimReason: form.claimReason, exceptionVersion: selected.value!.version, evidenceAttachmentIds: selectedEvidenceIds.value })); if (claim && selected.value) selected.value = { ...selected.value, status: 'PENDING_FINANCE_CONFIRMATION', claim } }
async function submitClaim() { const claim = selected.value?.claim; if (!claim || !await confirm({ title: '确认提交索赔', description: '提交后索赔将进入审核流程，请确认金额、币种和证据引用无误。', confirmLabel: '提交索赔' })) return; await run('索赔提交', () => api.submitClaim(claim.id, { reason: form.claimActionReason, version: claim.version })) }
async function resolveClaim() { const claim = selected.value?.claim; if (!claim) return; const approvedAmount = form.claimResult === 'REJECTED' ? null : Number(form.approvedAmount); await run('索赔结果更新', () => api.resolveClaim(claim.id, { status: form.claimResult, reason: form.resultReason, approvedAmount, resolvedAt: new Date().toISOString(), version: claim.version })) }
async function financeConfirm() { const claim = selected.value?.claim; if (!claim || !await confirm({ title: '确认财务结果', description: '财务确认会影响异常解决条件并写入审计，请核对索赔结果。', confirmLabel: '财务确认' })) return; await run('财务确认', () => api.financeConfirmClaim(claim.id, { reason: form.financeReason, version: claim.version })) }
async function closeClaim() { const claim = selected.value?.claim; if (!claim || !await confirm({ title: '确认关闭索赔', description: '关闭后索赔不能继续处理，请确认财务结果和处理记录完整。', confirmLabel: '关闭索赔', danger: true })) return; await run('索赔关闭', () => api.closeClaim(claim.id, { reason: form.closeReason, version: claim.version })) }

watch(() => route.fullPath, async () => {
  Object.assign(filters, parseExceptionQuery(route.query))
  await load()
  const exceptionId = typeof route.query.exceptionId === 'string' && /^\d+$/.test(route.query.exceptionId) ? route.query.exceptionId : undefined
  if (exceptionId) await loadSelected(exceptionId)
  else selected.value = undefined
}, { immediate: true })
onMounted(() => { if (canManage.value) void loadAssignees() })
</script>

<template>
  <section class="exception-page">
    <div class="page-heading"><div><span class="kicker">异常与索赔</span><h1>异常管理</h1><p>在同一工作台完成分配、处理、证据、索赔、财务确认与关闭。</p></div><button class="btn btn--secondary" :disabled="loading" @click="load">{{ loading ? '刷新中…' : '刷新' }}</button></div>
    <form @submit.prevent="search">
      <FilterToolbar advanced :submitting="loading" @search="search" @reset="reset">
      <label><span>异常类型</span><select v-model="filters.exceptionType"><option :value="undefined">全部</option><option value="ADDRESS">地址</option><option value="CUSTOMS">海关</option><option value="TRANSPORT">运输</option><option value="OTHER">其他</option></select></label>
      <label><span>状态</span><select v-model="filters.status"><option :value="undefined">全部</option><option v-for="value in ['OPEN','PROCESSING','WAITING_PROVIDER_FEEDBACK','PENDING_FINANCE_CONFIRMATION','RESOLVED','CLOSED']" :key="value" :value="value">{{ value }}</option></select></label>
      <label><span>订单号</span><input v-model.trim="filters.orderNo" maxlength="64"></label><label><span>店铺 ID</span><input v-model.trim="filters.storeId"></label>
      <label><span>责任方</span><select v-model="filters.responsibleParty"><option :value="undefined">全部</option><option v-for="value in ['MERCHANT','PROVIDER','CUSTOMS','CUSTOMER','OTHER']" :key="value" :value="value">{{ value }}</option></select></label>
      <template #advanced><div class="filter-toolbar__main"><label><span>订单 ID</span><input v-model.trim="filters.orderId"></label><label><span>创建时间从</span><input v-model="filters.createdFrom" type="datetime-local"></label><label><span>创建时间至</span><input v-model="filters.createdTo" type="datetime-local"></label><label><span>排序字段</span><select v-model="filters.sortBy"><option value="createdAt">创建时间</option><option value="updatedAt">更新时间</option><option value="status">状态</option><option value="exceptionType">类型</option><option value="orderNo">订单号</option></select></label><label><span>排序方向</span><select v-model="filters.sortDirection"><option value="DESC">降序</option><option value="ASC">升序</option></select></label></div></template>
      </FilterToolbar>
    </form>

    <div class="panel table-panel"><DataState :loading="loading" :error="error" :error-code="errorCode" :status="errorStatus" :trace-id="errorTraceId" :retry="load" :empty="!rows.length"><div class="data-table-wrap"><table class="data-table"><thead><tr><th>异常号</th><th>类型</th><th>状态</th><th>订单</th><th>店铺</th><th>责任方</th><th>负责人</th><th>索赔</th><th>财务确认</th><th>创建时间</th><th>更新时间</th><th>版本</th><th>操作</th></tr></thead><tbody><tr v-for="item in rows" :key="String(item.id)"><td>{{ item.exceptionNo }} <CopyTextButton :value="item.exceptionNo" label="异常号" /></td><td>{{ item.exceptionType }}</td><td><StatusBadge :status="item.status" /></td><td>{{ item.orderNo || item.orderId }} <CopyTextButton v-if="item.orderNo" :value="item.orderNo" label="订单号" /></td><td>{{ item.storeName || item.storeId }}</td><td>{{ item.responsibleParty || '未设置' }}</td><td>{{ item.assignedToUserName || item.assignedToUserId || '未分配' }}</td><td><StatusBadge :status="item.claim?.status || '未发起'" /></td><td><StatusBadge :status="item.claim?.financeConfirmedAt ? '已确认' : '未确认'" /></td><td>{{ displayDate(item.createdAt) }}</td><td>{{ displayDate(item.updatedAt) }}</td><td>{{ item.version }}</td><td><button class="table-link" type="button" @click="selectCase(item)">详情</button></td></tr></tbody></table></div></DataState><ListPagination :page="filters.page" :page-size="filters.pageSize" :total="total" :total-pages="totalPages" :loading="loading" @update:page="syncRoute" @update:page-size="size => syncRoute(1, size)" /></div>

    <template v-if="selected">
      <div class="panel exception-summary"><div class="panel__heading"><div><h2>{{ selected.exceptionNo }} <CopyTextButton :value="selected.exceptionNo" label="异常号" /></h2><p>{{ selected.description }}</p></div><StatusBadge :status="selected.status" /></div><div class="detail-grid"><div><small>订单</small><strong>{{ selected.orderNo || selected.orderId }} <CopyTextButton v-if="selected.orderNo" :value="selected.orderNo" label="订单号" /></strong></div><div><small>店铺</small><strong>{{ selected.storeName || selected.storeId }}</strong></div><div><small>负责人</small><strong>{{ selected.assignedToUserName || selected.assignedToUserId || '未分配' }}</strong></div><div><small>责任方</small><strong>{{ selected.responsibleParty || '未设置' }}</strong></div><div><small>创建时间</small><strong>{{ displayDate(selected.createdAt) }}</strong></div><div><small>版本</small><strong>{{ selected.version }}</strong></div></div></div>
      <ActionError v-if="action.errorMessage.value" :message="action.errorMessage.value" :code="action.errorCode.value" :trace-id="action.errorTraceId.value" />
      <div v-if="canManage" class="exception-actions">
        <div class="panel action-card"><h2>负责人分配</h2><label>租户有效用户<select v-model="form.assignedToUserId" :disabled="!assignees.length"><option value="">{{ assignees.length ? '请选择负责人' : '暂无可分配用户' }}</option><option v-for="user in assignees" :key="String(user.id)" :value="String(user.id)">{{ user.displayName }}（{{ user.username }}）</option></select></label><p v-if="assigneeError" class="form-error">负责人列表加载失败：{{ assigneeError }}</p><label>责任方<select v-model="form.responsibleParty"><option v-for="value in ['MERCHANT','PROVIDER','CUSTOMS','CUSTOMER','OTHER']" :key="value">{{ value }}</option></select></label><label>原因<input v-model="form.assignReason" maxlength="1000"></label><button class="btn btn--primary" :disabled="action.submitting.value || !mutable || !form.assignedToUserId" @click="assign">{{ action.submitting.value ? '处理中…' : '分配' }}</button></div>
        <div class="panel action-card"><h2>状态推进</h2><label>目标状态<select v-model="form.targetStatus"><option v-for="value in allowedStatuses" :key="value" :value="value">{{ value }}</option></select></label><label>原因<input v-model="form.statusReason" maxlength="1000"></label><button class="btn btn--primary" :disabled="action.submitting.value || !mutable || !form.targetStatus" @click="transition">{{ form.targetStatus === 'CLOSED' ? '关闭异常' : '推进状态' }}</button></div>
        <div class="panel action-card"><h2>处理记录</h2><label>类型<select v-model="form.recordType"><option v-for="value in ['CONTACT','FOLLOW_UP','PROVIDER_FEEDBACK','INTERNAL_NOTE','OTHER']" :key="value">{{ value }}</option></select></label><label>内容<textarea v-model="form.recordContent" maxlength="4000"></textarea></label><button class="btn btn--primary" :disabled="action.submitting.value || !mutable || !form.recordContent.trim()" @click="addRecord">添加记录</button></div>
        <div class="panel action-card"><h2>证据上传</h2><label>文件（PDF/JPEG/PNG/WebP/TXT，最大 10 MiB）<input type="file" accept="application/pdf,image/jpeg,image/png,image/webp,text/plain" @change="chooseFile"></label><label>说明<textarea v-model="form.evidenceDescription" maxlength="1000"></textarea></label><button class="btn btn--primary" :disabled="action.submitting.value || !mutable || !evidenceFile" @click="upload">上传证据</button></div>
      </div>

      <div class="exception-detail-grid">
        <div class="panel"><h2>处理记录</h2><ul class="record-list"><li v-for="record in selected.handlingRecords || []" :key="String(record.id)"><b>{{ record.recordType }}</b><span>{{ record.content }}</span><small>{{ displayDate(record.createdAt) }}</small></li></ul><p v-if="!selected.handlingRecords?.length" role="status">暂无处理记录</p></div>
        <div class="panel"><h2>证据附件</h2><ul class="record-list"><li v-for="file in selected.evidenceAttachments || []" :key="String(file.id)"><b>{{ file.originalFileName }}</b><span>{{ file.description || '无说明' }} · {{ file.contentType }} · {{ file.fileSize }} B</span><small>SHA-256 {{ file.contentSha256 }} · {{ displayDate(file.createdAt) }}</small></li></ul><p v-if="!selected.evidenceAttachments?.length">暂无证据</p></div>
      </div>

      <div class="panel claim-panel"><h2>索赔与财务确认</h2><template v-if="!selected.claim"><div class="action-panel"><label>索赔原因<textarea v-model="form.claimReason" maxlength="1000" :aria-invalid="!form.claimReason.trim()"></textarea></label><label>金额<input v-model="form.claimAmount" type="number" min="0.01" step="0.01" :aria-invalid="Number(form.claimAmount) <= 0"></label><label>币种<input v-model="form.currency" maxlength="3" :aria-invalid="!regexCurrency.test(form.currency)"></label><label>引用证据（可多选）<select v-model="selectedEvidenceIds" multiple :disabled="!selected.evidenceAttachments?.length"><option v-for="file in selected.evidenceAttachments || []" :key="String(file.id)" :value="file.id">{{ file.originalFileName }} · {{ file.description || '无说明' }}</option></select></label><p v-if="!selected.evidenceAttachments?.length" class="form-error">请先上传至少一份证据，索赔只能引用当前异常下的证据。</p><p v-else-if="selected.status !== 'PROCESSING'" class="form-error">发起索赔前，请先将异常推进到 PROCESSING。</p><p v-else-if="!claimReady" class="form-error">请填写索赔原因、有效金额和三位币种，并选择至少一份证据。</p></div><button v-if="canManage" class="btn btn--primary" :disabled="action.submitting.value || !claimReady" @click="createClaim">{{ action.submitting.value ? '处理中…' : '发起索赔' }}</button></template><template v-else><div class="detail-grid"><div><small>索赔号</small><strong>{{ selected.claim.claimNo }} <CopyTextButton :value="selected.claim.claimNo" label="索赔号" /></strong></div><div><small>状态</small><StatusBadge :status="selected.claim.status" /></div><div><small>申请金额</small><strong>{{ displayMoney(selected.claim.claimAmount, selected.claim.currency) }}</strong></div><div><small>审核金额</small><strong>{{ displayMoney(selected.claim.resolvedAmount, selected.claim.currency) }}</strong></div><div><small>结果原因</small><strong>{{ selected.claim.resultReason || '—' }}</strong></div><div><small>财务确认</small><strong>{{ selected.claim.financeConfirmedByUserName || selected.claim.financeConfirmedByUserId || '未确认' }} / {{ displayDate(selected.claim.financeConfirmedAt) }}</strong></div></div><div v-if="canManage && mutable" class="claim-actions"><button v-if="selected.claim.status === 'OPEN'" class="btn btn--primary" :disabled="action.submitting.value" @click="submitClaim">{{ action.submitting.value ? '处理中…' : '提交索赔' }}</button><template v-if="selected.claim.status === 'SUBMITTED'"><label><span class="sr-only">索赔结果</span><select v-model="form.claimResult" aria-label="索赔结果"><option value="APPROVED">批准</option><option value="PARTIALLY_APPROVED">部分批准</option><option value="REJECTED">拒绝</option></select></label><input v-if="form.claimResult !== 'REJECTED'" v-model="form.approvedAmount" type="number" min="0" step="0.01" aria-label="实际批准金额" placeholder="实际批准金额"><input v-model="form.resultReason" maxlength="1000" aria-label="审核原因" placeholder="审核原因"><button class="btn btn--primary" :disabled="action.submitting.value || !form.resultReason" @click="resolveClaim">保存结果</button></template><button v-if="['APPROVED','PARTIALLY_APPROVED','REJECTED'].includes(selected.claim.status) && !selected.claim.financeConfirmedAt" class="btn btn--primary" :disabled="action.submitting.value" @click="financeConfirm">财务确认</button><button v-if="['APPROVED','PARTIALLY_APPROVED','REJECTED'].includes(selected.claim.status) && selected.claim.financeConfirmedAt" class="btn btn--secondary" :disabled="action.submitting.value" @click="closeClaim">关闭索赔</button></div></template></div>

      <div class="panel timeline-panel"><h2>统一处理时间线</h2><ol class="timeline"><li v-for="event in selected.timeline || []" :key="`${event.occurredAt}-${event.relatedId}-${event.eventType}`"><time>{{ displayDate(event.occurredAt) }}</time><div><b>{{ event.title }}</b><p>{{ event.description || event.eventType }}</p><small>{{ event.operatorName || event.operatorUserId || '系统' }} · {{ event.source }}<template v-if="event.statusBefore || event.statusAfter"> · {{ event.statusBefore || '—' }} → {{ event.statusAfter || '—' }}</template><template v-if="event.requestId"> · Request/Trace {{ event.requestId }} <CopyTextButton :value="event.requestId" label="Request/Trace ID" /></template></small></div></li></ol><p v-if="!selected.timeline?.length">暂无时间线事件</p></div>
    </template>
  </section>
</template>
