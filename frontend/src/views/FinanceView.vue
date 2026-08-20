<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CheckCircle2, ChevronRight, RefreshCw, Search } from '@lucide/vue'
import DataState from '@/components/DataState.vue'
import { useSubmit } from '@/composables/useSubmit'
import { getApiErrorMessage, toApiError } from '@/services/http'
import { useAuthStore } from '@/stores/auth'
import * as audit from '@/services/audit'
import * as billing from '@/services/billing'
import { displayLabel, displayValue } from '@/utils/display'
import { financeListFilter } from './workbenchTargetFilters'

type Tab = 'batches' | 'details' | 'reconciliations' | 'audit'

const auth = useAuthStore()
const route = useRoute()
const router = useRouter()
const submit = useSubmit()
const tab = ref<Tab>('batches')
const loading = ref(false)
const error = ref('')
const errorTraceId = ref<string>()
const rows = ref<Array<billing.BillBatch | billing.BillDetail | billing.Reconciliation | audit.AuditLog>>([])
const selectedBatch = ref<billing.BillBatch>()
const selectedBatchErrors = ref<billing.BillDetail[]>([])
const selectedBatchDetails = ref<billing.BillDetail[]>([])
const selectedReconciliation = ref<billing.Reconciliation>()
const selectedAudit = ref<audit.AuditLog>()
const lookupId = ref('')
const filterStatus = ref('')
const confirmForm = reactive({ resolutionType: 'ACCEPT', remark: '' })
const requestEpoch = ref(0)
const routeFilterNotice = ref('')

const canImport = computed(() => auth.hasPermission('finance:bill-import'))
const canReconcile = computed(() => auth.hasPermission('finance:reconcile'))
const canAudit = computed(() => auth.hasPermission('audit:read'))
const tabs = computed(() => [
  { key: 'batches' as const, label: '账单批次', visible: canImport.value || auth.hasPermission('billing:read') },
  { key: 'details' as const, label: '账单明细', visible: auth.hasPermission('billing:read') },
  { key: 'reconciliations' as const, label: '费用对账', visible: auth.hasPermission('billing:read') },
  { key: 'audit' as const, label: '财务审计记录', visible: canAudit.value },
].filter(item => item.visible))

function format(key: string, value: unknown) { return displayValue(key, value) }
async function copySubmitTrace() {
  if (!submit.errorTraceId.value) return
  try { await navigator.clipboard.writeText(submit.errorTraceId.value) } catch { /* 页面保留编号供人工记录 */ }
}
function idOf(value: { id?: string } | undefined) { return value?.id == null ? '' : String(value.id) }
function cell(row: unknown, key: string) { return format(key, (row as Record<string, unknown> | undefined)?.[key]) }
function openRow(row: unknown) {
  const value = row as Record<string, unknown>
  if (tab.value === 'batches') return selectBatch(value as unknown as billing.BillBatch)
  if (tab.value === 'details' && value.batchId != null) {
    tab.value = 'batches'
    lookupId.value = String(value.batchId)
    return lookup()
  }
  lookupId.value = value.id == null ? '' : String(value.id)
  return lookup()
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
  errorTraceId.value = undefined
  rows.value = []
  clearSelection()
  try {
    if (currentTab === 'batches') {
      rows.value = (await billing.listBillImportBatches({ status: filterStatus.value as billing.BillBatchStatus || undefined })).items
    } else if (currentTab === 'details') {
      rows.value = (await billing.listBillDetails({ status: filterStatus.value as billing.BillDetail['detailStatus'] || undefined })).items
    } else if (currentTab === 'reconciliations') {
      rows.value = (await billing.listReconciliations({ status: filterStatus.value as billing.Reconciliation['reconciliationStatus'] || undefined })).items
    } else if (canAudit.value) {
      rows.value = (await audit.listTenantAuditLogs()).items
    }
  } catch (cause) {
    if (epoch === requestEpoch.value) {
      const apiError = toApiError(cause)
      error.value = getApiErrorMessage(apiError, '财务数据加载失败，请稍后重试。')
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
    errorTraceId.value = apiError.traceId
  } finally {
    loading.value = false
  }
}

async function confirmDifference() {
  const reconciliation = selectedReconciliation.value
  if (!reconciliation?.id || reconciliation.reconciliationStatus !== 'PENDING_CONFIRMATION') return
  if (!window.confirm('确认提交对账差异处理？提交后将记录审计，不可直接撤回。')) return
  await submit.submit(async () => {
    if (!confirmForm.remark.trim()) throw new Error('请填写对账差异处理备注。')
    selectedReconciliation.value = await billing.confirmReconciliationDifference(reconciliation.id!, {
      resolutionType: confirmForm.resolutionType,
      remark: confirmForm.remark.trim(),
      version: reconciliation.version ?? 0,
    })
    await load()
  })
}

function setTab(next: Tab) {
  if (next === 'audit' && !canAudit.value) return
  if (next === tab.value && !filterStatus.value) { void load(); return }
  void router.replace({ query: { ...route.query, tab: next, status: undefined } })
}

function setFilter() {
  void router.replace({ query: { ...route.query, tab: tab.value, status: filterStatus.value || undefined } })
}

watch(() => auth.permissions, () => {
  if (!tabs.value.some(item => item.key === tab.value)) tab.value = tabs.value[0]?.key ?? 'batches'
}, { deep: true })
watch(() => route.fullPath, () => {
  const filter = financeListFilter(route.query)
  routeFilterNotice.value = filter.notice ?? ''
  if (filter.tab && tabs.value.some(item => item.key === filter.tab)) tab.value = filter.tab
  else if (filter.tab) routeFilterNotice.value = '当前账号没有访问该财务列表的权限。'
  filterStatus.value = filter.status ?? ''
  void load()
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
    <div v-if="!tabs.length" class="alert alert--error" role="alert">当前账号暂无财务查询权限，请联系租户管理员。</div>
    <div v-else class="panel lookup-bar">
      <label><span>{{ tab === 'batches' || tab === 'details' ? '批次 ID' : tab === 'reconciliations' ? '对账记录 ID' : '审计日志 ID' }}</span><input v-model.trim="lookupId" @keyup.enter="lookup" /></label>
      <label v-if="tab !== 'audit'"><span>状态筛选</span><select v-model="filterStatus" @change="setFilter"><option value="">全部状态</option><option v-if="tab === 'batches'" value="PROCESSING">处理中</option><option v-if="tab === 'batches'" value="PARTIAL_SUCCESS">部分成功</option><option v-if="tab === 'batches'" value="SUCCESS">成功</option><option v-if="tab === 'batches'" value="FAILED">失败</option><option v-if="tab === 'details'" value="IMPORTED">已导入</option><option v-if="tab === 'details'" value="MATCHED">已匹配</option><option v-if="tab === 'details'" value="ERROR">错误</option><option v-if="tab === 'reconciliations'" value="AUTO_CLOSED">自动关闭</option><option v-if="tab === 'reconciliations'" value="PENDING_CONFIRMATION">待确认</option><option v-if="tab === 'reconciliations'" value="CONFIRMED">已确认</option><option v-if="tab === 'reconciliations'" value="REJECTED">已驳回</option></select></label>
      <button class="btn btn--primary" type="button" :disabled="loading || !lookupId" @click="lookup"><Search :size="16" />查询详情</button>
      <button class="btn btn--secondary" type="button" :disabled="loading" @click="load">查看列表</button>
    </div>
    <div v-if="routeFilterNotice" class="alert alert--warning" role="status">{{ routeFilterNotice }}</div>
    <div v-if="submit.errorMessage.value" class="alert alert--error" role="alert"><b v-if="submit.errorCode.value">{{ submit.errorCode.value }}：</b>{{ submit.errorMessage.value }} <span v-if="submit.errorTraceId.value">追踪编号：{{ submit.errorTraceId.value }} <button class="text-button" type="button" @click="copySubmitTrace">复制</button></span></div>
    <DataState :loading="loading" :error="error" :trace-id="errorTraceId" :retry="load" :empty="!rows.length && !selectedBatch && !selectedReconciliation && !selectedAudit" empty-title="当前暂无财务记录">
      <div v-if="selectedBatch" class="finance-detail-stack">
        <div class="panel detail-grid">
          <div><small>批次号</small><span>{{ selectedBatch.batchNo || '-' }}</span></div><div><small>文件名</small><span>{{ selectedBatch.fileName || '-' }}</span></div><div><small>批次状态</small><span>{{ format('status', selectedBatch.status) }}</span></div><div><small>总行数</small><span>{{ selectedBatch.totalCount ?? '-' }}</span></div><div><small>成功行数</small><span>{{ selectedBatch.successCount ?? '-' }}</span></div><div><small>失败行数</small><span>{{ selectedBatch.failureCount ?? '-' }}</span></div>
        </div>
        <div class="panel table-panel"><h2>账单明细</h2><div v-if="!selectedBatchDetails.length" class="data-state">该批次暂无账单明细。</div><div v-else class="data-table-wrap"><table class="data-table"><thead><tr><th>物流商明细号</th><th>行号</th><th>订单 ID</th><th>物流单号</th><th>金额</th><th>币种</th><th>费用类型</th><th>状态</th></tr></thead><tbody><tr v-for="detail in selectedBatchDetails" :key="detail.id"><td>{{ detail.providerBillDetailNo || '-' }}</td><td>{{ detail.lineNo ?? '-' }}</td><td>{{ detail.shipmentOrderId || '-' }}</td><td>{{ detail.trackingNo || '-' }}</td><td>{{ detail.billedAmount ?? '-' }}</td><td>{{ detail.currency || '-' }}</td><td>{{ detail.feeType || '-' }}</td><td>{{ format('status', detail.detailStatus) }}</td></tr></tbody></table></div></div>
        <div class="panel table-panel"><h2>导入错误明细</h2><div v-if="!selectedBatchErrors.length" class="data-state">该批次暂无导入错误。</div><div v-else class="data-table-wrap"><table class="data-table"><thead><tr><th>行号</th><th>物流商明细号</th><th>物流单号</th><th>错误原因</th></tr></thead><tbody><tr v-for="detail in selectedBatchErrors" :key="detail.id"><td>{{ detail.lineNo ?? '-' }}</td><td>{{ detail.providerBillDetailNo || '-' }}</td><td>{{ detail.trackingNo || '-' }}</td><td>{{ detail.errorMessage || '-' }}</td></tr></tbody></table></div></div>
      </div>
      <div v-else-if="selectedReconciliation" class="panel finance-detail-stack">
        <div class="detail-grid"><div><small>对账记录 ID</small><span>{{ selectedReconciliation.id }}</span></div><div><small>订单 ID</small><span>{{ selectedReconciliation.shipmentOrderId || '-' }}</span></div><div><small>系统金额</small><span>{{ selectedReconciliation.systemAmount ?? '-' }}</span></div><div><small>账单金额</small><span>{{ selectedReconciliation.billedAmount ?? '-' }}</span></div><div><small>差异金额</small><span>{{ selectedReconciliation.differenceAmount ?? '-' }}</span></div><div><small>状态</small><span>{{ format('reconciliationStatus', selectedReconciliation.reconciliationStatus) }}</span></div><div><small>版本</small><span>{{ selectedReconciliation.version ?? '-' }}</span></div></div>
        <div v-if="selectedReconciliation.reconciliationStatus === 'PENDING_CONFIRMATION' && canReconcile" class="compact-form"><label><span>处理方式</span><input v-model.trim="confirmForm.resolutionType" maxlength="64" /></label><label><span>处理备注</span><textarea v-model.trim="confirmForm.remark" rows="3" maxlength="1000"></textarea></label><button class="btn btn--primary" type="button" :disabled="submit.submitting.value" @click="confirmDifference"><CheckCircle2 :size="16" />确认对账差异</button></div>
        <div v-else-if="selectedReconciliation.reconciliationStatus === 'PENDING_CONFIRMATION'" class="muted">当前账号没有确认对账差异的权限。</div>
      </div>
      <div v-else-if="selectedAudit" class="panel detail-grid"><div><small>日志 ID</small><span>{{ selectedAudit.id }}</span></div><div><small>资源类型</small><span>{{ format('resourceType', selectedAudit.resourceType) }}</span></div><div><small>操作类型</small><span>{{ format('actionType', selectedAudit.actionType) }}</span></div><div><small>处理结果</small><span>{{ format('resultStatus', selectedAudit.resultStatus) }}</span></div><div><small>发生时间</small><span>{{ format('occurredAt', selectedAudit.occurredAt) }}</span></div><div><small>详情</small><span>{{ JSON.stringify(selectedAudit.detail) }}</span></div></div>
      <div v-else class="panel table-panel"><div class="data-table-wrap"><table class="data-table"><thead><tr><th v-for="key in (tab === 'batches' ? ['batchNo', 'fileName', 'status', 'totalCount', 'successCount', 'failureCount'] : tab === 'details' ? ['providerBillDetailNo', 'shipmentOrderId', 'trackingNo', 'billedAmount', 'currency', 'detailStatus'] : tab === 'reconciliations' ? ['id', 'shipmentOrderId', 'systemAmount', 'billedAmount', 'differenceAmount', 'reconciliationStatus'] : ['id', 'resourceType', 'actionType', 'resultStatus', 'occurredAt'])" :key="key">{{ displayLabel(key) }}</th><th>操作</th></tr></thead><tbody><tr v-for="row in rows" :key="String((row as Record<string, unknown>).id)"><td v-for="key in (tab === 'batches' ? ['batchNo', 'fileName', 'status', 'totalCount', 'successCount', 'failureCount'] : tab === 'details' ? ['providerBillDetailNo', 'shipmentOrderId', 'trackingNo', 'billedAmount', 'currency', 'detailStatus'] : tab === 'reconciliations' ? ['id', 'shipmentOrderId', 'systemAmount', 'billedAmount', 'differenceAmount', 'reconciliationStatus'] : ['id', 'resourceType', 'actionType', 'resultStatus', 'occurredAt'])" :key="key">{{ cell(row, key) }}</td><td><button class="table-link" type="button" @click="openRow(row)">查看详情<ChevronRight :size="15" /></button></td></tr></tbody></table></div></div>
    </DataState>
    <div class="panel muted finance-gap-note">订单费用调整查询：当前后端尚未提供按租户查询 `fee_adjustment` 的正式接口，本页面不展示伪造数据；待后端补齐查询契约后再接入。</div>
  </section>
</template>
