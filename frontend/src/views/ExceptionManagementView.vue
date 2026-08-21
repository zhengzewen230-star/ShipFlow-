<script setup lang="ts">
import { computed, onMounted, reactive, ref } from 'vue'
import DataState from '@/components/DataState.vue'
import ActionError from '@/components/ActionError.vue'
import { useSubmit } from '@/composables/useSubmit'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notifications'
import { toApiError } from '@/services/http'
import * as api from '@/services/exceptions'
import type { ExceptionCase, ExceptionQuery, ExceptionStatus, ResponsibleParty } from '@/services/exceptions'
import { allowedExceptionTransitions, canMutateException, idList } from './exceptionWorkflow'

const auth = useAuthStore()
const notifications = useNotificationStore()
const action = useSubmit()
const loading = ref(false)
const error = ref('')
const errorTraceId = ref<string>()
const rows = ref<ExceptionCase[]>([])
const selected = ref<ExceptionCase>()
const total = ref(0)
const totalPages = ref(0)
const canManage = computed(() => auth.hasPermission('exception:manage'))
const mutable = computed(() => !!selected.value && canMutateException(selected.value.status))
const allowedStatuses = computed(() => selected.value ? allowedExceptionTransitions(selected.value.status) : [])

const filters = reactive<ExceptionQuery>({ page: 1, pageSize: 20, sortBy: 'createdAt', sortDirection: 'DESC' })
const form = reactive({ assignedToUserId: '', responsibleParty: 'PROVIDER' as ResponsibleParty, assignReason: '', targetStatus: '' as ExceptionStatus | '', statusReason: '', recordType: 'FOLLOW_UP' as api.HandlingRecord['recordType'], recordContent: '', evidenceDescription: '', claimAmount: '', currency: 'USD', claimReason: '', evidenceIds: '', claimActionReason: '', claimResult: 'APPROVED' as 'APPROVED' | 'PARTIALLY_APPROVED' | 'REJECTED', approvedAmount: '', resultReason: '', financeReason: '', closeReason: '' })
const evidenceFile = ref<File>()

function dateParam(value?: string) { return value ? new Date(value).toISOString() : undefined }
function displayDate(value?: string | null) { return value ? new Intl.DateTimeFormat('zh-CN', { dateStyle: 'medium', timeStyle: 'short', timeZone: 'UTC' }).format(new Date(value)) + ' UTC' : '—' }
function displayMoney(value?: number | null, currency?: string) { return value == null ? '—' : `${value.toFixed(2)} ${currency ?? ''}` }

async function load() {
  loading.value = true; error.value = ''; errorTraceId.value = undefined
  try {
    const result = await api.listExceptionCases({ ...filters, createdFrom: dateParam(filters.createdFrom), createdTo: dateParam(filters.createdTo) })
    rows.value = result.items; total.value = result.total; totalPages.value = result.totalPages
  } catch (cause) { const converted = toApiError(cause); error.value = converted.message; errorTraceId.value = converted.traceId }
  finally { loading.value = false }
}

async function selectCase(item: ExceptionCase) {
  loading.value = true; error.value = ''
  try { selected.value = await api.getExceptionCase(item.id); form.targetStatus = allowedExceptionTransitions(selected.value.status)[0] ?? '' }
  catch (cause) { const converted = toApiError(cause); error.value = converted.message; errorTraceId.value = converted.traceId }
  finally { loading.value = false }
}

async function refreshSelected() { if (selected.value) selected.value = await api.getExceptionCase(selected.value.id) }
async function run(label: string, operation: () => Promise<unknown>) {
  const result = await action.submit(operation)
  if (result === undefined) return
  notifications.push(`${label}成功`, 'success')
  await refreshSelected(); await load()
  if (selected.value) form.targetStatus = allowedExceptionTransitions(selected.value.status)[0] ?? ''
}
function search() { filters.page = 1; void load() }
function reset() { Object.assign(filters, { orderId: undefined, orderNo: undefined, storeId: undefined, exceptionType: undefined, status: undefined, responsibleParty: undefined, createdFrom: undefined, createdTo: undefined, page: 1, pageSize: 20, sortBy: 'createdAt', sortDirection: 'DESC' }); void load() }
function page(delta: number) { filters.page = Math.max(1, (filters.page ?? 1) + delta); void load() }
function chooseFile(event: Event) { evidenceFile.value = (event.target as HTMLInputElement).files?.[0] }

async function assign() { if (!selected.value || !form.assignedToUserId) return; await run('负责人分配', () => api.assignExceptionCase(selected.value!.id, { assignedToUserId: form.assignedToUserId, responsibleParty: form.responsibleParty, reason: form.assignReason, version: selected.value!.version })) }
async function transition() { if (!selected.value || !form.targetStatus) return; await run(form.targetStatus === 'CLOSED' ? '异常关闭' : '状态更新', () => api.transitionExceptionCase(selected.value!.id, { status: form.targetStatus as Exclude<ExceptionStatus, 'OPEN'>, reason: form.statusReason, version: selected.value!.version })) }
async function addRecord() { if (!selected.value || !form.recordContent.trim()) return; await run('处理记录添加', () => api.addHandlingRecord(selected.value!.id, { recordType: form.recordType, content: form.recordContent.trim(), version: selected.value!.version })); form.recordContent = '' }
async function upload() { if (!selected.value || !evidenceFile.value) return; if (evidenceFile.value.size > 10 * 1024 * 1024) { notifications.push('附件不得超过 10 MiB', 'error'); return } await run('证据上传', () => api.uploadEvidence(selected.value!.id, evidenceFile.value!, selected.value!.version, form.evidenceDescription)); evidenceFile.value = undefined; form.evidenceDescription = '' }
async function createClaim() { if (!selected.value) return; await run('索赔发起', () => api.createClaim(selected.value!.id, { claimAmount: Number(form.claimAmount), currency: form.currency.toUpperCase(), claimReason: form.claimReason, exceptionVersion: selected.value!.version, evidenceAttachmentIds: idList(form.evidenceIds) })) }
async function submitClaim() { const claim = selected.value?.claim; if (!claim) return; await run('索赔提交', () => api.submitClaim(claim.id, { reason: form.claimActionReason, version: claim.version })) }
async function resolveClaim() { const claim = selected.value?.claim; if (!claim) return; const approvedAmount = form.claimResult === 'REJECTED' ? null : Number(form.approvedAmount); await run('索赔结果更新', () => api.resolveClaim(claim.id, { status: form.claimResult, reason: form.resultReason, approvedAmount, resolvedAt: new Date().toISOString(), version: claim.version })) }
async function financeConfirm() { const claim = selected.value?.claim; if (!claim) return; await run('财务确认', () => api.financeConfirmClaim(claim.id, { reason: form.financeReason, version: claim.version })) }
async function closeClaim() { const claim = selected.value?.claim; if (!claim) return; await run('索赔关闭', () => api.closeClaim(claim.id, { reason: form.closeReason, version: claim.version })) }

onMounted(load)
</script>

<template>
  <section class="exception-page">
    <div class="page-heading"><div><span class="kicker">异常与索赔</span><h1>异常管理</h1><p>在同一工作台完成分配、处理、证据、索赔、财务确认与关闭。</p></div><button class="btn btn--secondary" :disabled="loading" @click="load">{{ loading ? '刷新中…' : '刷新' }}</button></div>
    <form class="panel compact-form exception-filter" @submit.prevent="search">
      <label><span>异常类型</span><select v-model="filters.exceptionType"><option :value="undefined">全部</option><option value="ADDRESS">地址</option><option value="CUSTOMS">海关</option><option value="TRANSPORT">运输</option><option value="OTHER">其他</option></select></label>
      <label><span>状态</span><select v-model="filters.status"><option :value="undefined">全部</option><option v-for="value in ['OPEN','PROCESSING','WAITING_PROVIDER_FEEDBACK','PENDING_FINANCE_CONFIRMATION','RESOLVED','CLOSED']" :key="value" :value="value">{{ value }}</option></select></label>
      <label><span>订单号</span><input v-model.trim="filters.orderNo" maxlength="64"></label><label><span>订单 ID</span><input v-model.trim="filters.orderId"></label><label><span>店铺 ID</span><input v-model.trim="filters.storeId"></label>
      <label><span>责任方</span><select v-model="filters.responsibleParty"><option :value="undefined">全部</option><option v-for="value in ['MERCHANT','PROVIDER','CUSTOMS','CUSTOMER','OTHER']" :key="value" :value="value">{{ value }}</option></select></label>
      <label><span>创建时间从</span><input v-model="filters.createdFrom" type="datetime-local"></label><label><span>创建时间至</span><input v-model="filters.createdTo" type="datetime-local"></label>
      <label><span>排序字段</span><select v-model="filters.sortBy"><option value="createdAt">创建时间</option><option value="updatedAt">更新时间</option><option value="status">状态</option><option value="exceptionType">类型</option><option value="orderNo">订单号</option></select></label>
      <label><span>排序方向</span><select v-model="filters.sortDirection"><option value="DESC">降序</option><option value="ASC">升序</option></select></label>
      <div class="page-actions"><button class="btn btn--primary" type="submit" :disabled="loading">查询</button><button class="btn btn--secondary" type="button" :disabled="loading" @click="reset">重置</button></div>
    </form>

    <div class="panel table-panel"><DataState :loading="loading" :error="error" :trace-id="errorTraceId" :retry="load" :empty="!rows.length"><div class="data-table-wrap"><table class="data-table"><thead><tr><th>异常号</th><th>类型</th><th>状态</th><th>订单</th><th>店铺</th><th>责任方</th><th>负责人</th><th>索赔</th><th>财务确认</th><th>创建时间</th><th>更新时间</th><th>版本</th><th>操作</th></tr></thead><tbody><tr v-for="item in rows" :key="String(item.id)"><td>{{ item.exceptionNo }}</td><td>{{ item.exceptionType }}</td><td><span class="status-badge">{{ item.status }}</span></td><td>{{ item.orderNo || item.orderId }}</td><td>{{ item.storeName || item.storeId }}</td><td>{{ item.responsibleParty || '未设置' }}</td><td>{{ item.assignedToUserName || item.assignedToUserId || '未分配' }}</td><td>{{ item.claim?.status || '未发起' }}</td><td>{{ item.claim?.financeConfirmedAt ? '已确认' : '未确认' }}</td><td>{{ displayDate(item.createdAt) }}</td><td>{{ displayDate(item.updatedAt) }}</td><td>{{ item.version }}</td><td><button class="table-link" type="button" @click="selectCase(item)">详情</button></td></tr></tbody></table></div></DataState><div class="table-pagination"><span>共 {{ total }} 条，第 {{ filters.page }} / {{ Math.max(totalPages, 1) }} 页</span><button class="btn btn--secondary" :disabled="loading || filters.page === 1" @click="page(-1)">上一页</button><button class="btn btn--secondary" :disabled="loading || (filters.page ?? 1) >= totalPages" @click="page(1)">下一页</button></div></div>

    <template v-if="selected">
      <div class="panel exception-summary"><div class="panel__heading"><div><h2>{{ selected.exceptionNo }}</h2><p>{{ selected.description }}</p></div><span class="status-badge">{{ selected.status }}</span></div><div class="detail-grid"><div><small>订单</small><strong>{{ selected.orderNo || selected.orderId }}</strong></div><div><small>店铺</small><strong>{{ selected.storeName || selected.storeId }}</strong></div><div><small>负责人</small><strong>{{ selected.assignedToUserName || selected.assignedToUserId || '未分配' }}</strong></div><div><small>责任方</small><strong>{{ selected.responsibleParty || '未设置' }}</strong></div><div><small>创建时间</small><strong>{{ displayDate(selected.createdAt) }}</strong></div><div><small>版本</small><strong>{{ selected.version }}</strong></div></div></div>
      <ActionError v-if="action.errorMessage.value" :message="action.errorMessage.value" :code="action.errorCode.value" :trace-id="action.errorTraceId.value" />
      <div v-if="canManage" class="exception-actions">
        <div class="panel action-card"><h2>负责人分配</h2><label>租户用户 ID<input v-model.trim="form.assignedToUserId"></label><label>责任方<select v-model="form.responsibleParty"><option v-for="value in ['MERCHANT','PROVIDER','CUSTOMS','CUSTOMER','OTHER']" :key="value">{{ value }}</option></select></label><label>原因<input v-model="form.assignReason" maxlength="1000"></label><button class="btn btn--primary" :disabled="action.submitting.value || !mutable || !form.assignedToUserId" @click="assign">{{ action.submitting.value ? '处理中…' : '分配' }}</button></div>
        <div class="panel action-card"><h2>状态推进</h2><label>目标状态<select v-model="form.targetStatus"><option v-for="value in allowedStatuses" :key="value" :value="value">{{ value }}</option></select></label><label>原因<input v-model="form.statusReason" maxlength="1000"></label><button class="btn btn--primary" :disabled="action.submitting.value || !mutable || !form.targetStatus" @click="transition">{{ form.targetStatus === 'CLOSED' ? '关闭异常' : '推进状态' }}</button></div>
        <div class="panel action-card"><h2>处理记录</h2><label>类型<select v-model="form.recordType"><option v-for="value in ['CONTACT','FOLLOW_UP','PROVIDER_FEEDBACK','INTERNAL_NOTE','OTHER']" :key="value">{{ value }}</option></select></label><label>内容<textarea v-model="form.recordContent" maxlength="4000"></textarea></label><button class="btn btn--primary" :disabled="action.submitting.value || !mutable || !form.recordContent.trim()" @click="addRecord">添加记录</button></div>
        <div class="panel action-card"><h2>证据上传</h2><label>文件（PDF/JPEG/PNG/WebP/TXT，最大 10 MiB）<input type="file" accept="application/pdf,image/jpeg,image/png,image/webp,text/plain" @change="chooseFile"></label><label>说明<textarea v-model="form.evidenceDescription" maxlength="1000"></textarea></label><button class="btn btn--primary" :disabled="action.submitting.value || !mutable || !evidenceFile" @click="upload">上传证据</button></div>
      </div>

      <div class="exception-detail-grid">
        <div class="panel"><h2>处理记录</h2><ul class="record-list"><li v-for="record in selected.handlingRecords || []" :key="String(record.id)"><b>{{ record.recordType }}</b><span>{{ record.content }}</span><small>{{ displayDate(record.createdAt) }}</small></li></ul><p v-if="!selected.handlingRecords?.length">暂无处理记录</p></div>
        <div class="panel"><h2>证据附件</h2><ul class="record-list"><li v-for="file in selected.evidenceAttachments || []" :key="String(file.id)"><b>{{ file.originalFileName }}</b><span>{{ file.description || '无说明' }} · {{ file.contentType }} · {{ file.fileSize }} B</span><small>SHA-256 {{ file.contentSha256 }} · {{ displayDate(file.createdAt) }}</small></li></ul><p v-if="!selected.evidenceAttachments?.length">暂无证据</p></div>
      </div>

      <div class="panel claim-panel"><h2>索赔与财务确认</h2><template v-if="!selected.claim"><div class="action-panel"><label>索赔原因<textarea v-model="form.claimReason" maxlength="1000"></textarea></label><label>金额<input v-model="form.claimAmount" type="number" min="0.01" step="0.01"></label><label>币种<input v-model="form.currency" maxlength="3"></label><label>证据 ID（逗号分隔）<input v-model="form.evidenceIds"></label></div><button v-if="canManage" class="btn btn--primary" :disabled="action.submitting.value || !mutable || !form.claimReason || !form.claimAmount || !idList(form.evidenceIds).length" @click="createClaim">发起索赔</button></template><template v-else><div class="detail-grid"><div><small>索赔号</small><strong>{{ selected.claim.claimNo }}</strong></div><div><small>状态</small><strong>{{ selected.claim.status }}</strong></div><div><small>申请金额</small><strong>{{ displayMoney(selected.claim.claimAmount, selected.claim.currency) }}</strong></div><div><small>审核金额</small><strong>{{ displayMoney(selected.claim.resolvedAmount, selected.claim.currency) }}</strong></div><div><small>结果原因</small><strong>{{ selected.claim.resultReason || '—' }}</strong></div><div><small>财务确认</small><strong>{{ selected.claim.financeConfirmedByUserName || selected.claim.financeConfirmedByUserId || '未确认' }} / {{ displayDate(selected.claim.financeConfirmedAt) }}</strong></div></div><div v-if="canManage && mutable" class="claim-actions"><button v-if="selected.claim.status === 'OPEN'" class="btn btn--primary" :disabled="action.submitting.value" @click="submitClaim">提交索赔</button><template v-if="selected.claim.status === 'SUBMITTED'"><select v-model="form.claimResult"><option value="APPROVED">批准</option><option value="PARTIALLY_APPROVED">部分批准</option><option value="REJECTED">拒绝</option></select><input v-if="form.claimResult !== 'REJECTED'" v-model="form.approvedAmount" type="number" min="0" step="0.01" placeholder="实际批准金额"><input v-model="form.resultReason" maxlength="1000" placeholder="审核原因"><button class="btn btn--primary" :disabled="action.submitting.value || !form.resultReason" @click="resolveClaim">保存结果</button></template><button v-if="['APPROVED','PARTIALLY_APPROVED','REJECTED'].includes(selected.claim.status) && !selected.claim.financeConfirmedAt" class="btn btn--primary" :disabled="action.submitting.value" @click="financeConfirm">财务确认</button><button v-if="['APPROVED','PARTIALLY_APPROVED','REJECTED'].includes(selected.claim.status) && selected.claim.financeConfirmedAt" class="btn btn--secondary" :disabled="action.submitting.value" @click="closeClaim">关闭索赔</button></div></template></div>

      <div class="panel timeline-panel"><h2>统一处理时间线</h2><ol class="timeline"><li v-for="event in selected.timeline || []" :key="`${event.occurredAt}-${event.relatedId}-${event.eventType}`"><time>{{ displayDate(event.occurredAt) }}</time><div><b>{{ event.title }}</b><p>{{ event.description || event.eventType }}</p><small>{{ event.operatorName || event.operatorUserId || '系统' }} · {{ event.source }}<template v-if="event.statusBefore || event.statusAfter"> · {{ event.statusBefore || '—' }} → {{ event.statusAfter || '—' }}</template><template v-if="event.requestId"> · Request/Trace {{ event.requestId }}</template></small></div></li></ol><p v-if="!selected.timeline?.length">暂无时间线事件</p></div>
    </template>
  </section>
</template>
