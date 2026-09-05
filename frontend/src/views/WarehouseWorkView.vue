<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { ClipboardCheck, FileText, PackageCheck, Printer, Ruler, Search, Truck, Warehouse as WarehouseIcon } from '@lucide/vue'
import ActionError from '@/components/ActionError.vue'
import CopyTextButton from '@/components/CopyTextButton.vue'
import DataState from '@/components/DataState.vue'
import ListPagination from '@/components/ListPagination.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { getApiErrorMessage, toApiError } from '@/services/http'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { useSubmit } from '@/composables/useSubmit'
import * as tracking from '@/services/tracking'
import * as warehouse from '@/services/warehouse'
import { displayValue, formatChargeableWeight } from '@/utils/display'
import { canCreateSfOrder, canSaveMeasurement, measurementValidationMessage } from './warehouseWorkRules'
import { warehouseListFilter } from './workbenchTargetFilters'

const route = useRoute()
const router = useRouter()
const { confirm } = useConfirmAction()
const loading = ref(false)
const error = ref('')
const errorTraceId = ref<string>()
const errorCode = ref<string>()
const errorStatus = ref<number>()
const success = ref('')
const rows = ref<warehouse.WarehouseWorkItem[]>([])
const selected = ref<warehouse.WarehouseWorkItem>()
const selectedId = ref<string>()
const activeStatus = ref<warehouse.WarehouseWorkStatus>()
const orderNo = ref('')
const page = ref(1)
const pageSize = ref(20)
const total = ref(0)
const totalPages = ref(0)
const routeFilterNotice = ref('')
const overviewNotice = ref('')
const overview = ref<warehouse.WarehouseOverview>()
const trackingEvents = ref<tracking.TrackingEvent[]>([])
const trackingLoading = ref(false)
const trackingError = ref('')
const trackingErrorTraceId = ref<string>()
const sfLabelUrl = ref('')
const sfInvoiceUrl = ref('')
const submit = useSubmit()
const action = reactive({
  version: 0,
  actualWeight: null as number | null,
  actualLength: null as number | null,
  actualWidth: null as number | null,
  actualHeight: null as number | null,
  trackingNo: '',
})
const customs = reactive({ certName: '', certCardNo: '', certType: '', frontPic: '', backPic: '' })
const measurementError = computed(() => measurementValidationMessage(action))
const measurementSaveEnabled = computed(() => canSaveMeasurement(selected.value, action, submit.submitting.value))
const sfCreateEnabled = computed(() => canCreateSfOrder(selected.value, submit.submitting.value))

type Tab = { label: string; status?: warehouse.WarehouseWorkStatus; count?: number; enabled: boolean; hint?: string }
const tabs = computed<Tab[]>(() => [
  { label: '全部', status: undefined, count: total.value, enabled: true },
  { label: '待入库', status: 'PENDING_INBOUND', count: overview.value?.pendingInbound, enabled: true },
  { label: '待复称/测方', status: 'INBOUND', count: overview.value?.pendingMeasurement, enabled: true },
  { label: '待打单/贴标', status: undefined, count: undefined, enabled: false, hint: '等待 V013 迁移和顺丰官方面单契约' },
  { label: '待交接/出库', status: 'READY_FOR_OUTBOUND', count: overview.value?.pendingOutbound, enabled: true },
  { label: '已出库', status: 'OUTBOUND', count: overview.value?.inTransit, enabled: true },
])

const detailFields = computed(() => {
  const item = selected.value
  if (!item) return []
  return [
    ['业务订单号', item.businessOrderNo],
    ['顺丰运单号', item.sfTrackingNo || '尚未生成'],
    ['所属商户', item.tenantName],
    ['目的国', displayValue('destinationCountry', item.destinationCountry)],
    ['仓库状态', displayValue('status', item.warehouseStatus)],
    ['物流状态', displayValue('status', item.logisticsStatus)],
    ['申报重量', `${item.declaredWeight} kg`],
    ['申报尺寸', `${item.declaredLength} × ${item.declaredWidth} × ${item.declaredHeight} cm`],
    ['实测重量', item.actualWeight == null ? '尚未复称' : `${item.actualWeight} kg`],
    ['实测尺寸', item.actualLength == null ? '尚未复称' : `${item.actualLength} × ${item.actualWidth} × ${item.actualHeight} cm`],
    ['体积重', item.actualVolumeWeight == null ? '尚未复称' : `${item.actualVolumeWeight} kg`],
    ['计费重量', formatChargeableWeight(item.chargeableWeight)],
    ['申报费用', `${item.estimatedFee} ${item.currency ?? ''}`],
    ['实际费用', `${item.currentFee} ${item.currency ?? ''}`],
    ['费用差额', `${item.feeDifference} ${item.currency ?? ''}`],
    ['差额预警', item.feeAlert ? '需要费用确认' : '正常'],
  ]
})

async function load() {
  if (loading.value) return
  loading.value = true
  error.value = ''
  errorTraceId.value = undefined
  success.value = ''
  try {
    const pageResult = await warehouse.listWarehouseWork({ page: page.value, pageSize: pageSize.value, status: activeStatus.value, orderNo: orderNo.value || undefined })
    rows.value = pageResult.items
    total.value = pageResult.total
    totalPages.value = pageResult.totalPages
    try {
      overview.value = await warehouse.getWarehouseOverview()
      overviewNotice.value = ''
    } catch (cause) {
      const apiError = toApiError(cause)
      overview.value = undefined
      overviewNotice.value = apiError.status === 403
        ? '当前账号没有仓库概览权限；列表数据仍按后端授权范围显示。'
        : '仓库概览暂时不可用；列表数据不受影响。'
    }
  } catch (cause) {
    const apiError = toApiError(cause)
    error.value = getApiErrorMessage(apiError, '仓库作业数据加载失败，请稍后重试。')
    errorCode.value = apiError.code
    errorStatus.value = apiError.status
    errorTraceId.value = apiError.traceId
  } finally {
    loading.value = false
  }
}

async function open(item: warehouse.WarehouseWorkItem, sync = true) {
  if (sync) { await router.replace({ query: { ...route.query, recordId: item.id, page: page.value, pageSize: pageSize.value } }); return }
  selectedId.value = item.id
  error.value = ''
  trackingError.value = ''
  trackingErrorTraceId.value = undefined
  trackingEvents.value = []
  try {
    selected.value = await warehouse.getWarehouseWorkItem(item.id)
    Object.assign(action, {
      version: selected.value.version,
      actualWeight: selected.value.actualWeight ?? null,
      actualLength: selected.value.actualLength ?? null,
      actualWidth: selected.value.actualWidth ?? null,
      actualHeight: selected.value.actualHeight ?? null,
      trackingNo: selected.value.sfTrackingNo ?? '',
    })
    await loadTracking(item.id)
  } catch (cause) {
    const apiError = toApiError(cause)
    error.value = getApiErrorMessage(apiError, '订单详情加载失败，请稍后重试。')
    errorTraceId.value = apiError.traceId
  }
}
async function loadTracking(orderId: string) {
  trackingLoading.value = true
  trackingError.value = ''
  trackingErrorTraceId.value = undefined
  try {
    trackingEvents.value = (await tracking.listTrackingEvents(orderId, { page: 1, pageSize: 50 })).items
  } catch (cause) {
    const apiError = toApiError(cause)
    trackingError.value = getApiErrorMessage(apiError, '轨迹加载失败，请稍后重试。')
    trackingErrorTraceId.value = apiError.traceId
  } finally {
    trackingLoading.value = false
  }
}
function retryTracking() { return selected.value ? loadTracking(selected.value.id) : undefined }

async function run(kind: 'inbound' | 'measure' | 'outbound') {
  if (!selected.value) return
  if (kind === 'outbound' && !await confirm({ title: '确认交接出库', description: '该操作会推进订单状态，请确认当前物流单号和货物信息无误。', confirmLabel: '确认出库' })) return
  const result = await submit.submit(async () => {
    success.value = ''
    if (kind === 'inbound') return warehouse.confirmInbound(selected.value!.id, action.version)
    if (kind === 'measure') return warehouse.submitMeasurement(selected.value!.id, {
      actualWeight: action.actualWeight as number,
      actualLength: action.actualLength as number,
      actualWidth: action.actualWidth as number,
      actualHeight: action.actualHeight as number,
      version: action.version,
    })
    return warehouse.confirmOutbound(selected.value!.id, {
      trackingNo: action.trackingNo.trim(),
      version: action.version,
    })
  })
  if (!result) return
  await open(selected.value, false)
  await load()
  success.value = ({ inbound: '已完成入库。', measure: '复称数据已保存，计费重量和费用已重新计算。', outbound: '已完成交接出库。' } as const)[kind]
}

async function runSf(operation: warehouse.SfOperation) {
  if (!selected.value) return
  if (operation === 'CANCEL_ORDER' && !await confirm({ title: '确认取消顺丰订单', description: '取消后不能恢复，请确认后继续。', confirmLabel: '取消顺丰订单', danger: true })) return
  const result = await submit.submit(async () => {
    success.value = ''
    const payload = operation === 'UPLOAD_CERTIFY' ? JSON.stringify(customs) : ''
    return warehouse.executeSfOperation(selected.value!.id, operation, payload)
  })
  if (!result) return
  if (result.trackingNo) action.trackingNo = result.trackingNo
  if (result.labelUrl) sfLabelUrl.value = result.labelUrl
  if (result.invoiceUrl) sfInvoiceUrl.value = result.invoiceUrl
  await open(selected.value, false)
  success.value = `${warehouse.sfOperationLabel(operation)}已提交，requestID：${result.requestId}`
}

function selectTab(tab: Tab) {
  if (!tab.enabled) return
  activeStatus.value = tab.status
  page.value = 1
  syncRoute()
}

function syncRoute() {
  void router.replace({ query: {
    ...route.query,
    status: activeStatus.value || undefined,
    orderNo: orderNo.value || undefined,
    page: page.value > 1 ? String(page.value) : undefined,
    pageSize: pageSize.value !== 20 ? String(pageSize.value) : undefined,
    recordId: undefined,
  } })
}

function submitFilters() {
  page.value = 1
  syncRoute()
}

function resetFilters() {
  orderNo.value = ''
  page.value = 1
  syncRoute()
}

function closeDetail() {
  selected.value = undefined
  selectedId.value = undefined
  trackingEvents.value = []
  trackingError.value = ''
  trackingErrorTraceId.value = undefined
  sfLabelUrl.value = ''
  sfInvoiceUrl.value = ''
  void router.replace({ query: { ...route.query, recordId: undefined } })
}
function changePage(next: number) { page.value = next; syncRoute() }
function changePageSize(next: number) { pageSize.value = next; page.value = 1; syncRoute() }

watch(() => route.fullPath, async () => {
  const filter = warehouseListFilter(route.query)
  activeStatus.value = filter.status
  routeFilterNotice.value = filter.notice ?? ''
  orderNo.value = typeof route.query.orderNo === 'string' ? route.query.orderNo : ''
  const requestedPage = typeof route.query.page === 'string' && /^\d+$/.test(route.query.page) ? Number(route.query.page) : 1
  page.value = requestedPage > 0 ? requestedPage : 1
  const requestedPageSize = typeof route.query.pageSize === 'string' && ['20', '50', '100'].includes(route.query.pageSize) ? Number(route.query.pageSize) : 20
  pageSize.value = requestedPageSize
  await load()
  const recordId = typeof route.query.recordId === 'string' ? route.query.recordId : ''
  const item = recordId ? rows.value.find(row => String(row.id) === recordId) : undefined
  if (item) await open(item, false)
}, { immediate: true })
</script>

<template>
  <section class="warehouse-work">
    <div v-if="routeFilterNotice" class="alert alert--warning" role="status">{{ routeFilterNotice }}</div>
    <div v-if="overviewNotice" class="alert alert--warning" role="status">{{ overviewNotice }}</div>
    <div class="page-heading">
      <div>
        <span class="kicker">仓库作业</span>
        <h1>仓库作业工作台</h1>
        <p>按当前租户和仓库授权范围处理入库、复称测方、打单贴标、出库交接和轨迹查询。</p>
      </div>
      <WarehouseIcon :size="32" aria-hidden="true" />
    </div>

    <div class="warehouse-tabs" role="tablist" aria-label="仓库状态筛选">
      <button v-for="tab in tabs" :key="tab.label" class="warehouse-tab" :class="{ active: activeStatus === tab.status, disabled: !tab.enabled }" :disabled="!tab.enabled" :title="tab.hint" @click="selectTab(tab)">
        <span>{{ tab.label }}</span>
        <strong v-if="tab.count !== undefined">{{ tab.count }}</strong>
        <small v-else-if="tab.hint">{{ tab.hint }}</small>
      </button>
    </div>

    <form class="warehouse-filters" @submit.prevent="submitFilters">
      <label><span>业务订单号</span><input v-model.trim="orderNo" placeholder="输入商家订单号" /></label>
      <button class="btn btn--primary" type="submit"><Search :size="16" />查询</button>
      <button class="btn btn--secondary" type="button" @click="resetFilters">重置</button>
    </form>

    <DataState :loading="loading" :error="error" :error-code="errorCode" :status="errorStatus" :trace-id="errorTraceId" :retry="load" :empty="!rows.length && !selected" empty-title="当前没有待处理业务">
      <div v-if="selected" class="warehouse-detail panel">
        <div class="warehouse-detail__header">
          <div><span class="kicker">作业详情</span><h2>{{ selected.businessOrderNo }} <CopyTextButton :value="selected.businessOrderNo" label="订单号" /></h2></div>
          <button class="btn btn--secondary" @click="closeDetail">返回列表</button>
        </div>

        <div class="warehouse-detail__grid"><div v-for="field in detailFields" :key="field[0]"><small>{{ field[0] }}</small><strong>{{ field[1] }}</strong></div></div>

        <section class="warehouse-operation">
          <h3>DWS 称重测方</h3>
          <div class="form-grid">
            <label><span>实测重量（kg）</span><input v-model.number="action.actualWeight" type="number" min="0.001" step="0.001" /></label>
            <label><span>长（cm）</span><input v-model.number="action.actualLength" type="number" min="0.001" step="0.001" /></label>
            <label><span>宽（cm）</span><input v-model.number="action.actualWidth" type="number" min="0.001" step="0.001" /></label>
            <label><span>高（cm）</span><input v-model.number="action.actualHeight" type="number" min="0.001" step="0.001" /></label>
          </div>
          <p v-if="measurementError" class="form-note form-note--error" role="alert">{{ measurementError }}</p>
          <button class="btn btn--secondary" :disabled="!measurementSaveEnabled" @click="run('measure')"><Ruler :size="16" />保存复称</button>
        </section>

        <section class="warehouse-operation">
          <h3>顺丰面单与清关资料</h3>
          <p class="form-note">所有供应商调用均由后端发起。缺少官方沙箱签名和报文契约时，后端会返回配置错误，不会伪造成功。</p>
          <div v-if="selected" class="form-grid customs-fields">
            <label><span>证件名称</span><input v-model.trim="customs.certName" autocomplete="off" /></label>
            <label><span>证件号码（测试资料）</span><input v-model.trim="customs.certCardNo" autocomplete="off" /></label>
            <label><span>证件类型</span><input v-model.trim="customs.certType" autocomplete="off" /></label>
            <label><span>正面图片引用（测试资料）</span><input v-model.trim="customs.frontPic" autocomplete="off" /></label>
            <label><span>背面图片引用（测试资料）</span><input v-model.trim="customs.backPic" autocomplete="off" /></label>
          </div>
          <div class="page-actions">
            <button class="btn btn--secondary" :disabled="!sfCreateEnabled" @click="runSf('CREATE_ORDER')"><PackageCheck :size="16" />创建顺丰订单</button>
            <button class="btn btn--secondary" :disabled="submit.submitting.value || !selected.sfTrackingNo" @click="runSf('PRINT_ORDER')"><Printer :size="16" />获取发货面单</button>
            <button class="btn btn--secondary" :disabled="submit.submitting.value || !selected.sfTrackingNo" @click="runSf('QUERY_ORDER')">查询顺丰状态</button>
            <button class="btn btn--secondary" :disabled="submit.submitting.value || !selected.sfTrackingNo" @click="runSf('UPLOAD_CERTIFY')"><FileText :size="16" />上传清关资料</button>
            <button class="btn btn--secondary" :disabled="submit.submitting.value || !selected.sfTrackingNo || selected.logisticsStatus === 'OUTBOUND'" @click="runSf('CANCEL_ORDER')">取消顺丰订单</button>
          </div>
        </section>

        <section class="warehouse-operation">
          <h3>出库交接</h3>
          <label class="tracking-input"><span>顺丰运单号</span><input v-model.trim="action.trackingNo" placeholder="顺丰创建成功后由后端回填" /></label>
          <div class="page-actions">
            <button class="btn btn--secondary" :disabled="submit.submitting.value || selected.logisticsStatus !== 'PENDING_INBOUND'" @click="run('inbound')"><ClipboardCheck :size="16" />确认入库</button>
            <button class="btn btn--primary" :disabled="submit.submitting.value || selected.logisticsStatus !== 'READY_FOR_OUTBOUND' || !action.trackingNo" @click="run('outbound')"><Truck :size="16" />确认交接出库</button>
          </div>
          <small v-if="selected.logisticsStatus !== 'READY_FOR_OUTBOUND'" class="operation-hint">当前状态为“{{ displayValue('status', selected.logisticsStatus) }}”，不允许交接出库。</small>
        </section>

        <section class="warehouse-operation">
          <h3>物流轨迹</h3>
          <DataState :loading="trackingLoading" :error="trackingError" :trace-id="trackingErrorTraceId" :retry="retryTracking" :empty="!trackingEvents.length" empty-title="暂无轨迹事件">
            <div class="data-table-wrap"><table class="data-table"><thead><tr><th>时间</th><th>物流单号</th><th>事件</th><th>说明</th><th>处理状态</th></tr></thead><tbody><tr v-for="event in trackingEvents" :key="event.id"><td>{{ displayValue('eventTime', event.eventTime) }}</td><td>{{ event.trackingNo }} <CopyTextButton v-if="event.trackingNo" :value="event.trackingNo" label="物流单号" /></td><td>{{ event.eventCode }}</td><td>{{ event.eventDescription || '—' }}</td><td><StatusBadge :status="event.processStatus" :label="displayValue('processStatus', event.processStatus)" /></td></tr></tbody></table></div>
          </DataState>
        </section>

        <div v-if="sfLabelUrl || sfInvoiceUrl" class="sf-artifacts">
          <a v-if="sfLabelUrl" :href="sfLabelUrl" target="_blank" rel="noopener">查看/打印顺丰面单</a>
          <a v-if="sfInvoiceUrl" :href="sfInvoiceUrl" target="_blank" rel="noopener">查看商业发票</a>
        </div>
        <div v-if="success" class="alert alert--success" role="status">{{ success }}</div>
        <ActionError :message="submit.errorMessage.value" :code="submit.errorCode.value" :trace-id="submit.errorTraceId.value" />
      </div>

      <div v-else class="table-panel panel">
        <div class="data-table-wrap"><table class="data-table"><thead><tr><th>业务单号</th><th>顺丰单号</th><th>所属商户</th><th>目的国</th><th>申报重量</th><th>实测规格</th><th>实测重量</th><th>计费重量</th><th>费用差额预警</th><th>仓库状态</th><th>操作</th></tr></thead><tbody><tr v-for="item in rows" :key="item.id"><td>{{ item.businessOrderNo }} <CopyTextButton :value="item.businessOrderNo" label="订单号" /></td><td>{{ item.sfTrackingNo || '尚未生成' }} <CopyTextButton v-if="item.sfTrackingNo" :value="item.sfTrackingNo" label="顺丰单号" /></td><td>{{ item.tenantName }}</td><td>{{ displayValue('destinationCountry', item.destinationCountry) }}</td><td>{{ item.declaredWeight }} kg</td><td>{{ item.actualLength == null ? '尚未复称' : `${item.actualLength} × ${item.actualWidth} × ${item.actualHeight} cm` }}</td><td>{{ item.actualWeight == null ? '尚未复称' : `${item.actualWeight} kg` }}</td><td>{{ formatChargeableWeight(item.chargeableWeight) }}</td><td><StatusBadge :status="item.feeAlert ? 'WARNING' : 'NORMAL'" :label="item.feeAlert ? `${item.feeDifference} ${item.currency}` : '正常'" /></td><td><StatusBadge :status="item.warehouseStatus" :label="displayValue('status', item.warehouseStatus)" /></td><td><button class="table-link" type="button" :disabled="selectedId === item.id" @click="open(item)">查看详情</button></td></tr></tbody></table></div>
        <p v-if="!rows.length" class="data-state">当前筛选条件下暂无数据</p>
        <ListPagination :page="page" :page-size="pageSize" :total="total" :total-pages="totalPages" :loading="loading" @update:page="changePage" @update:page-size="changePageSize" />
      </div>
    </DataState>
  </section>
</template>

<style scoped>
.warehouse-tabs { display: flex; flex-wrap: wrap; gap: .5rem; margin: 1rem 0; }
.warehouse-tab { display: inline-flex; align-items: center; gap: .55rem; min-height: 42px; padding: .7rem .9rem; border: 1px solid var(--slate-200); border-radius: .45rem; color: var(--navy-800); background: #fff; cursor: pointer; }
.warehouse-tab.active { border-color: var(--cyan-500); color: var(--cyan-700); background: #f0fbfc; }
.warehouse-tab.disabled { opacity: .55; cursor: not-allowed; }
.warehouse-tab strong { min-width: 1.4rem; text-align: center; }
.warehouse-tab small { color: var(--slate-500); }
.warehouse-filters { display: flex; align-items: end; gap: .8rem; margin-bottom: 1rem; }
.warehouse-filters label, .tracking-input { display: flex; min-width: 240px; flex-direction: column; gap: .35rem; color: var(--navy-800); font-size: .8rem; font-weight: 700; }
.warehouse-filters input, .tracking-input input, .warehouse-operation input { min-height: 42px; padding: .55rem .7rem; border: 1px solid var(--slate-300); border-radius: .45rem; }
.warehouse-detail { padding: 1.2rem; }
.warehouse-detail__header, .page-actions { display: flex; align-items: center; justify-content: space-between; gap: .7rem; }
.warehouse-detail__grid { display: grid; grid-template-columns: repeat(4, minmax(0, 1fr)); gap: .8rem; margin: 1rem 0; }
.warehouse-detail__grid div { min-width: 0; padding: .75rem; border: 1px solid var(--slate-200); border-radius: .45rem; }
.warehouse-detail__grid small { display: block; color: var(--slate-500); font-size: .75rem; }
.warehouse-detail__grid strong { display: block; margin-top: .25rem; overflow-wrap: anywhere; color: var(--navy-900); font-size: .9rem; }
.warehouse-operation { padding: 1rem 0; border-top: 1px solid var(--slate-200); }
.warehouse-operation h3 { margin: 0 0 .8rem; }
.warehouse-operation .form-grid { margin-bottom: .8rem; }
.warehouse-operation .tracking-input { margin-bottom: .8rem; }
.form-note--error { color: #b44951; }
.operation-hint { display: block; margin-top: .7rem; color: var(--slate-500); }
.fee-alert { color: #b44951; font-weight: 700; }.fee-ok { color: #16856d; }
.warehouse-pagination { display: flex; align-items: center; justify-content: center; gap: 1rem; padding: 1rem; color: var(--slate-600); font-size: .85rem; }
@media (max-width: 900px) { .warehouse-detail__grid { grid-template-columns: repeat(2, minmax(0, 1fr)); } }
@media (max-width: 600px) { .warehouse-filters { align-items: stretch; flex-direction: column; }.warehouse-filters label { min-width: 0; }.warehouse-detail__grid { grid-template-columns: 1fr; }.page-actions { align-items: stretch; flex-direction: column; }.page-actions .btn { width: 100%; }.warehouse-pagination { align-items: stretch; flex-direction: column; text-align: center; } }
.customs-fields { margin: .8rem 0; }
.sf-artifacts { display: flex; flex-wrap: wrap; gap: .8rem; margin: .8rem 0; }
.sf-artifacts a { color: var(--cyan-700); text-decoration: underline; }
</style>
