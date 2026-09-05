<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CheckCircle2, ChevronRight, RefreshCw, Search } from '@lucide/vue'
import ActionError from '@/components/ActionError.vue'
import CopyTextButton from '@/components/CopyTextButton.vue'
import DataState from '@/components/DataState.vue'
import ListPagination from '@/components/ListPagination.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { useSubmit } from '@/composables/useSubmit'
import { getApiErrorMessage, toApiError } from '@/services/http'
import { useAuthStore } from '@/stores/auth'
import { useNotificationStore } from '@/stores/notifications'
import * as audit from '@/services/audit'
import * as billing from '@/services/billing'
import { displayLabel, displayValue, formatMoney } from '@/utils/display'
import { financeListFilter } from './workbenchTargetFilters'

type Tab = 'batches' | 'details' | 'reconciliations' | 'audit'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const submit = useSubmit()
const notifications = useNotificationStore()
const { confirm } = useConfirmAction()
const tab = ref<Tab>('batches')
const loading = ref(false)
const error = ref('')
const errorTraceId = ref<string>()
const errorCode = ref<string>()
const errorStatus = ref<number>()
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalPages = ref(0)
const rows = ref<Array<billing.BillBatch | billing.BillDetail | billing.Reconciliation | audit.AuditLog>>([])
const selectedBatch = ref<billing.BillBatch>()
const selectedBatchErrors = ref<billing.BillDetail[]>([])
const selectedBatchDetails = ref<billing.BillDetail[]>([])
const selectedReconciliation = ref<billing.Reconciliation>()
const selectedAudit = ref<audit.AuditLog>()
const lookupId = ref('')
const filterStatus = ref('')
const confirmForm = reactive({ resolutionType: 'ACCEPT', remark: '' })
const importProviderId = ref('')
const importFile = ref<File>()
const requestEpoch = ref(0)
const routeFilterNotice = ref('')

const canImport = computed(() => auth.hasPermission('finance:bill-import'))
const canReconcile = computed(() => auth.hasPermission('finance:reconcile'))
const canAudit = computed(() => auth.hasPermission('audit:read'))
function chooseImportFile(event: Event) { importFile.value = (event.target as HTMLInputElement).files?.[0] }
async function importCsv() {
  if (!importFile.value || !importProviderId.value) return
  await submit.submit(async () => {
    const batch = await billing.importBillingCsv(importFile.value!, importProviderId.value)
    tab.value = 'batches'
    await selectBatch(batch)
    notifications.push('账单导入完成，批次统计已刷新。', 'success')
  })
}
const tabs = computed(() => [
  { key: 'batches' as const, label: '账单批次', visible: canImport.value || auth.hasPermission('billing:read') },
  { key: 'details' as const, label: '账单明细', visible: auth.hasPermission('billing:read') },
  { key: 'reconciliations' as const, label: '费用对账', visible: auth.hasPermission('billing:read') },
  { key: 'audit' as const, label: '财务审计记录', visible: canAudit.value },
].filter(item => item.visible))

function format(key: string, value: unknown) { return displayValue(key, value) }
function idOf(value: { id?: string } | undefined) { return value?.id == null ? '' : String(value.id) }
function cell(row: unknown, key: string) {
  const values = row as Record<string, unknown> | undefined
  const value = values?.[key]
  if (['billedAmount', 'systemAmount', 'differenceAmount'].includes(key)) return formatMoney(value, values?.currency)
  return format(key, value)
}
function openRow(row: unknown) {
  const value = row as Record<string, unknown>
  if (tab.value === 'batches') return void router.replace({ query: { ...route.query, recordId: String(value.id), page: page.value, pageSize: pageSize.value } })
  if (tab.value === 'details' && value.batchId != null) {
    return void router.replace({ query: { tab: 'batches', recordId: String(value.batchId), page: 1, pageSize: pageSize.value } })
  }
  if (value.id != null) void router.replace({ query: { ...route.query, recordId: String(value.id), page: page.value, pageSize: pageSize.value } })
}

function clearSelection() {
  selectedBatch.value = undefined
  selectedBatchErrors.value = []
  selectedBatchDetails.value = []
  selectedReconciliation.value = undefined
  selectedAudit.value = undefined
  lookupId.value = ''
}

async function load() {
  if (loading.value) return
  const currentTab = tab.value
  const epoch = ++requestEpoch.value
  loading.value = true
  error.value = ''
  errorCode.value = undefined
  errorStatus.value = undefined
  errorTraceId.value = undefined
  rows.value = []
  clearSelection()
  try {
    let result
    if (currentTab === 'batches') {
      result = await billing.listBillImportBatches({ status: filterStatus.value as billing.BillBatchStatus || undefined, page: page.value, pageSize: pageSize.value })
    } else if (currentTab === 'details') {
      result = await billing.listBillDetails({ status: filterStatus.value as billing.BillDetail['detailStatus'] || undefined, page: page.value, pageSize: pageSize.value })
    } else if (currentTab === 'reconciliations') {
      result = await billing.listReconciliations({ status: filterStatus.value as billing.Reconciliation['reconciliationStatus'] || undefined, page: page.value, pageSize: pageSize.value })
    } else if (canAudit.value) {
      result = await audit.listTenantAuditLogs({ page: page.value, pageSize: pageSize.value })
    }
    if (result) { rows.value = result.items; total.value = result.total; totalPages.value = result.totalPages }
  } catch (cause) {
    if (epoch === requestEpoch.value) {
      const apiError = toApiError(cause)
      error.value = getApiErrorMessage(apiError, '财务数据加载失败，请稍后重试。')
      errorCode.value = apiError.code
      errorStatus.value = apiError.status
      errorTraceId.value = apiError.traceId
    }
  } finally {
    if (epoch === requestEpoch.value) loading.value = false
  }
}

async function selectBatch(batch: billing.BillBatch) {
  const id = idOf(batch)
  if (!id) return
  lookupId.value = id
  loading.value = true
  error.value = ''
  errorTraceId.value = undefined
  selectedBatch.value = undefined
  selectedBatchErrors.value = []
  selectedBatchDetails.value = []
  try {
    const [detail, errors, allDetails] = await Promise.all([
      billing.getBillImportBatch(id),
      billing.listBillImportErrors(id),
      billing.listBillDetails({ batchId: id }),
    ])
    selectedBatch.value = detail
    selectedBatchErrors.value = errors.items
    selectedBatchDetails.value = allDetails.items
  } catch (cause) {
    const apiError = toApiError(cause)
    error.value = getApiErrorMessage(apiError, '账单批次详情加载失败，请稍后重试。')
    errorCode.value = apiError.code
    errorStatus.value = apiError.status
    errorTraceId.value = apiError.traceId
  } finally {
    loading.value = false
  }
}

async function lookup() {
  const id = lookupId.value.trim()
  if (!id) return
  loading.value = true
  error.value = ''
  errorTraceId.value = undefined
  selectedReconciliation.value = undefined
  selectedAudit.value = undefined
  try {
    if (tab.value === 'batches' || tab.value === 'details') {
      await selectBatch({ id } as billing.BillBatch)
    } else if (tab.value === 'reconciliations') {
      selectedReconciliation.value = await billing.getReconciliation(id)
    } else if (canAudit.value) {
      selectedAudit.value = await audit.getTenantAuditLog(id)
    }
  } catch (cause) {
    const apiError = toApiError(cause)
    error.value = getApiErrorMessage(apiError, '详情查询失败，请稍后重试。')
    errorCode.value = apiError.code
    errorStatus.value = apiError.status
    errorTraceId.value = apiError.traceId
  } finally {
    loading.value = false
  }
}

async function confirmDifference() {
  const reconciliation = selectedReconciliation.value
  if (!reconciliation?.id || reconciliation.reconciliationStatus !== 'PENDING_CONFIRMATION') return
  if (!await confirm({ title: '确认对账差异', description: '系统金额、供应商金额和差异金额均以后端真实数据为准。确认后将写入审计记录。', confirmLabel: '确认差异' })) return
  await submit.submit(async () => {
    if (!confirmForm.remark.trim()) throw new Error('请填写对账差异处理备注。')
    selectedReconciliation.value = await billing.confirmReconciliationDifference(reconciliation.id!, {
      resolutionType: confirmForm.resolutionType,
      remark: confirmForm.remark.trim(),
      version: reconciliation.version ?? 0,
    })
    notifications.push('对账差异已确认。', 'success')
  })
}

async function rejectDifference() {
  const reconciliation = selectedReconciliation.value
  if (!reconciliation?.id || reconciliation.reconciliationStatus !== 'PENDING_CONFIRMATION' || !confirmForm.remark.trim()) return
  if (!await confirm({ title: '确认驳回对账', description: '驳回将保留当前差异和原因，并写入完整人工处理历史。', confirmLabel: '驳回差异', danger: true })) return
  await submit.submit(async () => {
    selectedReconciliation.value = await billing.rejectReconciliationDifference(reconciliation.id!, { remark: confirmForm.remark.trim(), version: reconciliation.version ?? 0 })
    notifications.push('对账差异已驳回。', 'success')
  })
}

async function addComment() {
  const reconciliation = selectedReconciliation.value
  if (!reconciliation?.id || reconciliation.reconciliationStatus === 'AUTO_CLOSED' || !confirmForm.remark.trim()) return
  await submit.submit(async () => {
    selectedReconciliation.value = await billing.addReconciliationComment(reconciliation.id!, { remark: confirmForm.remark.trim(), version: reconciliation.version ?? 0 })
    notifications.push('补充说明已保存。', 'success')
  })
}

function setTab(next: Tab) {
  if (next === 'audit' && !canAudit.value) return
  if (next === tab.value && !filterStatus.value) { void load(); return }
  void router.replace({ query: { tab: next, page: 1, pageSize: pageSize.value } })
}

function setFilter() {
  void router.replace({ query: { tab: tab.value, status: filterStatus.value || undefined, page: 1, pageSize: pageSize.value } })
}
function setPage(next: number) { void router.replace({ query: { ...route.query, recordId: undefined, page: next, pageSize: pageSize.value } }) }
function setPageSize(next: number) { void router.replace({ query: { ...route.query, recordId: undefined, page: 1, pageSize: next } }) }

watch(() => auth.permissions, () => {
  if (!tabs.value.some(item => item.key === tab.value)) tab.value = tabs.value[0]?.key ?? 'batches'
}, { deep: true })
watch(() => route.fullPath, async () => {
  const filter = financeListFilter(route.query)
  routeFilterNotice.value = filter.notice ?? ''
  if (filter.tab && tabs.value.some(item => item.key === filter.tab)) tab.value = filter.tab
  else if (filter.tab) routeFilterNotice.value = '当前账号没有访问该财务列表的权限。'
  filterStatus.value = filter.status ?? ''
  page.value = filter.page
  pageSize.value = filter.pageSize
  await load()
  if (filter.recordId) { lookupId.value = filter.recordId; await lookup() }
}, { immediate: true })
</script>

<template>
  <section>
    <div class="page-heading">
      <div><span class="kicker">财务工作台</span><h1>账单与对账</h1><p>仅查看当前租户授权范围内的账单、费用对账和财务审计记录。</p></div>
      <button class="btn btn--secondary" type="button" :disabled="loading" @click="load"><RefreshCw :size="16" />刷新</button>
    </div>
    <div class="panel finance-tabs" role="tablist" aria-label="财务功能">
      <button v-for="item in tabs" :key="item.key" class="tab-button" :class="{ 'tab-button--active': tab === item.key }" type="button" role="tab" :aria-selected="tab === item.key" @click="setTab(item.key)">{{ item.label }}</button>
    </div>
    <div v-if="canImport" class="panel compact-form"><h2>导入供应商账单 CSV</h2><label><span>物流商 ID</span><input v-model.trim="importProviderId" inputmode="numeric" /></label><label><span>账单文件</span><input type="file" accept="text/csv,.csv" @change="chooseImportFile" /></label><button class="btn btn--primary" type="button" :disabled="submit.submitting.value || !importProviderId || !importFile" @click="importCsv">{{ submit.submitting.value ? '处理中…' : '导入账单' }}</button></div>
    <div v-if="!tabs.length" class="alert alert--error" role="alert">当前账号暂无财务查询权限，请联系租户管理员。</div>
    <div v-else class="panel lookup-bar">
      <label><span>{{ tab === 'batches' || tab === 'details' ? '批次 ID' : tab === 'reconciliations' ? '对账记录 ID' : '审计日志 ID' }}</span><input v-model.trim="lookupId" @keyup.enter="lookup" /></label>
      <label v-if="tab !== 'audit'"><span>状态筛选</span><select v-model="filterStatus" @change="setFilter"><option value="">全部状态</option><option v-if="tab === 'batches'" value="PROCESSING">处理中</option><option v-if="tab === 'batches'" value="PARTIAL_SUCCESS">部分成功</option><option v-if="tab === 'batches'" value="SUCCESS">成功</option><option v-if="tab === 'batches'" value="FAILED">失败</option><option v-if="tab === 'details'" value="IMPORTED">已导入</option><option v-if="tab === 'details'" value="MATCHED">已匹配</option><option v-if="tab === 'details'" value="ERROR">错误</option><option v-if="tab === 'reconciliations'" value="AUTO_CLOSED">自动关闭</option><option v-if="tab === 'reconciliations'" value="PENDING_CONFIRMATION">待确认</option><option v-if="tab === 'reconciliations'" value="CONFIRMED">已确认</option><option v-if="tab === 'reconciliations'" value="REJECTED">已驳回</option></select></label>
      <button class="btn btn--primary" type="button" :disabled="loading || !lookupId" @click="lookup"><Search :size="16" />查询详情</button>
      <button class="btn btn--secondary" type="button" :disabled="loading" @click="load">查看列表</button>
    </div>
    <div v-if="routeFilterNotice" class="alert alert--warning" role="status">{{ routeFilterNotice }}</div>
    <ActionError :message="submit.errorMessage.value" :code="submit.errorCode.value" :trace-id="submit.errorTraceId.value" />
    <DataState :loading="loading" :error="error" :error-code="errorCode" :status="errorStatus" :trace-id="errorTraceId" :retry="load" :empty="!rows.length && !selectedBatch && !selectedReconciliation && !selectedAudit" empty-title="当前暂无财务记录">
      <div v-if="selectedBatch" class="finance-detail-stack">
        <div class="panel detail-grid">
          <div><small>批次号</small><span>{{ selectedBatch.batchNo || '-' }} <CopyTextButton v-if="selectedBatch.batchNo" :value="selectedBatch.batchNo" label="批次号" /></span></div><div><small>文件名</small><span>{{ selectedBatch.fileName || '-' }}</span></div><div><small>批次状态</small><StatusBadge :status="selectedBatch.status || ''" :label="format('status', selectedBatch.status)" /></div><div><small>总行数</small><span>{{ selectedBatch.totalCount ?? '-' }}</span></div><div><small>成功行数</small><span>{{ selectedBatch.successCount ?? '-' }}</span></div><div><small>错误行数</small><span>{{ selectedBatch.failureCount ?? '-' }}</span></div><div><small>重复行数</small><span>{{ selectedBatch.duplicateCount ?? 0 }}</span></div><div><small>导入时间</small><span>{{ format('importedAt', selectedBatch.importedAt) }}</span></div>
        </div>
        <div class="panel table-panel"><h2>账单明细</h2><div v-if="!selectedBatchDetails.length" class="data-state">该批次暂无账单明细。</div><div v-else class="data-table-wrap"><table class="data-table"><thead><tr><th>物流商明细号</th><th>行号</th><th>订单 ID</th><th>物流单号</th><th>金额</th><th>币种</th><th>费用类型</th><th>状态</th></tr></thead><tbody><tr v-for="detail in selectedBatchDetails" :key="detail.id"><td>{{ detail.providerBillDetailNo || '-' }}</td><td>{{ detail.lineNo ?? '-' }}</td><td>{{ detail.shipmentOrderId || '-' }}</td><td>{{ detail.trackingNo || '-' }}</td><td>{{ formatMoney(detail.billedAmount, detail.currency) }}</td><td>{{ detail.currency || '-' }}</td><td>{{ format('feeType', detail.feeType) }}</td><td>{{ format('status', detail.detailStatus) }}</td></tr></tbody></table></div></div>
        <div class="panel table-panel"><h2>导入错误明细</h2><div v-if="!selectedBatchErrors.length" class="data-state">该批次暂无导入错误。</div><div v-else class="data-table-wrap"><table class="data-table"><thead><tr><th>行号</th><th>内部明细号</th><th>脱敏原始行</th><th>错误原因</th><th>处理状态</th></tr></thead><tbody><tr v-for="detail in selectedBatchErrors" :key="detail.id"><td>{{ detail.lineNo ?? '-' }}</td><td>{{ detail.providerBillDetailNo || '-' }}</td><td>{{ detail.rawLineMasked || '-' }}</td><td>{{ format('errorMessage', detail.errorMessage) }}</td><td>{{ format('errorHandlingStatus', detail.errorHandlingStatus) }}</td></tr></tbody></table></div></div>
      </div>
      <div v-else-if="selectedReconciliation" class="panel finance-detail-stack">
        <div class="detail-grid"><div><small>对账记录 ID</small><span>{{ selectedReconciliation.id }}</span></div><div><small>订单 ID</small><span>{{ selectedReconciliation.shipmentOrderId || '-' }}</span></div><div><small>系统金额</small><span>{{ selectedReconciliation.systemAmount ?? '-' }}</span></div><div><small>账单金额</small><span>{{ selectedReconciliation.billedAmount ?? '-' }}</span></div><div><small>差异金额</small><span>{{ selectedReconciliation.differenceAmount ?? '-' }}</span></div><div><small>状态</small><StatusBadge :status="selectedReconciliation.reconciliationStatus || ''" :label="format('reconciliationStatus', selectedReconciliation.reconciliationStatus)" /></div><div><small>版本</small><span>{{ selectedReconciliation.version ?? '-' }}</span></div></div>
        <div v-if="selectedReconciliation.reconciliationStatus !== 'AUTO_CLOSED' && canReconcile" class="compact-form"><label><span>处理方式</span><input v-model.trim="confirmForm.resolutionType" maxlength="64" /></label><label><span>处理备注</span><textarea v-model.trim="confirmForm.remark" rows="3" maxlength="1000" :aria-invalid="!confirmForm.remark.trim()"></textarea></label><p v-if="!confirmForm.remark.trim()" class="form-error">确认、驳回和补充说明都必须填写原因。</p><button v-if="selectedReconciliation.reconciliationStatus === 'PENDING_CONFIRMATION'" class="btn btn--primary" type="button" :disabled="submit.submitting.value || !confirmForm.remark" @click="confirmDifference"><CheckCircle2 :size="16" />{{ submit.submitting.value ? '处理中…' : '确认差异' }}</button><button v-if="selectedReconciliation.reconciliationStatus === 'PENDING_CONFIRMATION'" class="btn btn--secondary" type="button" :disabled="submit.submitting.value || !confirmForm.remark" @click="rejectDifference">{{ submit.submitting.value ? '处理中…' : '驳回差异' }}</button><button class="btn btn--secondary" type="button" :disabled="submit.submitting.value || !confirmForm.remark" @click="addComment">{{ submit.submitting.value ? '处理中…' : '补充说明' }}</button></div>
        <div v-else-if="selectedReconciliation.reconciliationStatus === 'PENDING_CONFIRMATION'" class="muted">当前账号没有确认对账差异的权限。</div>
        <div class="detail-grid"><div><small>差异原因</small><span>{{ selectedReconciliation.differenceReason || '-' }}</span></div><div><small>负责人</small><span>{{ selectedReconciliation.responsibleUserId || '待处理' }}</span></div></div>
        <div class="panel table-panel"><h2>人工处理历史</h2><div v-if="!selectedReconciliation.actionHistory?.length" class="data-state" role="status">暂无人工处理记录。</div><div v-else class="data-table-wrap"><table class="data-table"><thead><tr><th>动作</th><th>状态变化</th><th>说明</th><th>操作人</th><th>时间</th><th>Request/Trace</th></tr></thead><tbody><tr v-for="history in selectedReconciliation.actionHistory" :key="String(history.id)"><td>{{ history.actionType }}</td><td>{{ history.statusBefore || '-' }} → {{ history.statusAfter || '-' }}</td><td>{{ history.remark || '-' }}</td><td>{{ history.operatorUserId || '-' }}</td><td>{{ format('occurredAt', history.occurredAt) }}</td><td>{{ history.requestId || '-' }} <CopyTextButton v-if="history.requestId" :value="history.requestId" label="Request/Trace ID" /></td></tr></tbody></table></div></div>
      </div>
      <div v-else-if="selectedAudit" class="panel detail-grid"><div><small>日志 ID</small><span>{{ selectedAudit.id }}</span></div><div><small>资源类型</small><span>{{ format('resourceType', selectedAudit.resourceType) }}</span></div><div><small>操作类型</small><span>{{ format('actionType', selectedAudit.actionType) }}</span></div><div><small>处理结果</small><span>{{ format('resultStatus', selectedAudit.resultStatus) }}</span></div><div><small>发生时间</small><span>{{ format('occurredAt', selectedAudit.occurredAt) }}</span></div><div><small>详情</small><span>{{ JSON.stringify(selectedAudit.detail) }}</span></div></div>
      <div v-else class="panel table-panel"><div class="data-table-wrap"><table class="data-table"><thead><tr><th v-for="key in (tab === 'batches' ? ['batchNo', 'fileName', 'status', 'totalCount', 'successCount', 'failureCount', 'duplicateCount', 'importedAt'] : tab === 'details' ? ['providerBillDetailNo', 'shipmentOrderId', 'trackingNo', 'billedAmount', 'currency', 'detailStatus', 'errorHandlingStatus'] : tab === 'reconciliations' ? ['id', 'shipmentOrderId', 'systemAmount', 'billedAmount', 'differenceAmount', 'reconciliationStatus', 'responsibleUserId'] : ['id', 'resourceType', 'actionType', 'resultStatus', 'occurredAt'])" :key="key">{{ displayLabel(key) }}</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="String((row as Record<string, unknown>).id)"><td v-for="key in (tab === 'batches' ? ['batchNo', 'fileName', 'status', 'totalCount', 'successCount', 'failureCount', 'duplicateCount', 'importedAt'] : tab === 'details' ? ['providerBillDetailNo', 'shipmentOrderId', 'trackingNo', 'billedAmount', 'currency', 'detailStatus', 'errorHandlingStatus'] : tab === 'reconciliations' ? ['id', 'shipmentOrderId', 'systemAmount', 'billedAmount', 'differenceAmount', 'reconciliationStatus', 'responsibleUserId'] : ['id', 'resourceType', 'actionType', 'resultStatus', 'occurredAt'])" :key="key"><StatusBadge v-if="key.toLowerCase().includes('status')" :status="String((row as Record<string, unknown>)[key] ?? '')" :label="cell(row, key)" /><template v-else>{{ cell(row, key) }}</template><CopyTextButton v-if="['batchNo', 'trackingNo'].includes(key) && (row as Record<string, unknown>)[key]" :value="String((row as Record<string, unknown>)[key])" :label="displayLabel(key)" /></td><td><button class="table-link" type="button" @click="openRow(row)">查看详情<ChevronRight :size="15" /></button></td></tr></tbody></table></div></div>
    </DataState>
    <ListPagination v-if="!selectedBatch && !selectedReconciliation && !selectedAudit" :page="page" :page-size="pageSize" :total="total" :total-pages="totalPages" :loading="loading" @update:page="setPage" @update:page-size="setPageSize" />
    <div class="panel muted finance-gap-note">订单费用调整查询：当前后端尚未提供按租户查询 `fee_adjustment` 的正式接口，本页面不展示伪造数据；待后端补齐查询契约后再接入。</div>
  </section>
</template>
