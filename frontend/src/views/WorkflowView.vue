<script setup lang="ts">
import { computed, reactive, ref, watch } from 'vue'
import { useRoute, useRouter } from 'vue-router'
import { CheckCircle2, ChevronRight, Plus, Search, Trash2 } from '@lucide/vue'
import ActionError from '@/components/ActionError.vue'
import CopyTextButton from '@/components/CopyTextButton.vue'
import DataState from '@/components/DataState.vue'
import ListPagination from '@/components/ListPagination.vue'
import StatusBadge from '@/components/StatusBadge.vue'
import { useConfirmAction } from '@/composables/useConfirmAction'
import { useSubmit } from '@/composables/useSubmit'
import { getApiErrorMessage, toApiError } from '@/services/http'
import { useAuthStore } from '@/stores/auth'
import * as audit from '@/services/audit'
import * as billing from '@/services/billing'
import * as exceptions from '@/services/exceptions'
import * as orders from '@/services/orders'
import * as quotes from '@/services/quotes'
import type { Quote, QuoteFeeDetail, QuoteValidation } from '@/services/quotes'
import type { ApiPage } from '@/types/api'
import * as tracking from '@/services/tracking'
import * as warehouse from '@/services/warehouse'
import { displayLabel, displayValue } from '@/utils/display'
import { exceptionListFilter } from './workbenchTargetFilters'
import { defaultQuoteFilter, parseQuoteQuery, toQuoteApiQuery, toQuoteRouteQuery, type QuoteFilterState } from './quoteQuery'
import { defaultOrderFilter, parseOrderQuery, toOrderApiQuery, toOrderRouteQuery, type OrderFilterState } from './orderQuery'

const props = defineProps<{ domain: 'quotes' | 'orders' | 'warehouse' | 'tracking' | 'exceptions' | 'billing' | 'audit' }>()
const route = useRoute()
const router = useRouter()
const auth = useAuthStore()
const { confirm } = useConfirmAction()
const loading = ref(false)
const error = ref('')
const errorCode = ref<string>()
const errorTraceId = ref<string>()
const rows = ref<Record<string, unknown>[]>([])
const quoteFilter = reactive<QuoteFilterState>({ ...defaultQuoteFilter })
const orderFilter = reactive<OrderFilterState>({ ...defaultOrderFilter })
const orderPage = ref<ApiPage<orders.ShipmentOrder>>({ items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 })
const selectedOrderIds = ref<string[]>([])
const quotePage = ref<ApiPage<Quote>>({ items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 })
const auditPage = ref<ApiPage<audit.AuditLog>>({ items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 })
const selected = ref<Record<string, unknown>>()
const lookupId = ref('')
const tenantId = ref('')
const requestEpoch = ref(0)
const routeFilterNotice = ref('')
const quoteValidation = ref<QuoteValidation>()
const trackingEvents = ref<tracking.TrackingEvent[]>([])
const trackingLoading = ref(false)
const trackingError = ref('')
const trackingErrorCode = ref<string>()
const trackingErrorTraceId = ref<string>()
const priceConfirmation = ref<orders.PriceConfirmationView>()
const priceConfirmationError = ref('')
const priceConfirmationErrorCode = ref<string>()
const priceConfirmationErrorTraceId = ref<string>()
const successMessage = ref('')
const showOrderForm = ref(false)
const createdOrder = ref<orders.ShipmentOrder>()
const submit = useSubmit()
const action = reactive({ version: 0, trackingNo: '', actualWeight: 0, actualLength: 0, actualWidth: 0, actualHeight: 0 })
const emptyAddress = (countryCode = '') => ({ contactName: '', phone: '', companyName: '', email: '', countryCode, city: '', addressLine1: '', postalCode: '' })
const emptyItem = () => ({ sku: '', productName: '', quantity: 1, unitPrice: 0, currency: 'CNY', hsCode: '', countryOfOrigin: '' })
const orderForm = reactive({ senderAddress: emptyAddress(), receiverAddress: emptyAddress(), items: [emptyItem()] })
const countryCodePattern = /^[A-Z]{2}$/
const countryOptions = [
  { code: 'CN', label: '中国（CN）' }, { code: 'US', label: '美国（US）' }, { code: 'CA', label: '加拿大（CA）' },
  { code: 'AU', label: '澳大利亚（AU）' }, { code: 'JP', label: '日本（JP）' }, { code: 'KR', label: '韩国（KR）' },
  { code: 'GB', label: '英国（GB）' }, { code: 'DE', label: '德国（DE）' }, { code: 'FR', label: '法国（FR）' },
]
const destinationCountryLabel = computed(() => countryOptions.find(country => country.code === orderForm.receiverAddress.countryCode)?.label ?? `目的国家（${orderForm.receiverAddress.countryCode}）`)

const domainInfo = {
  quotes: ['报价管理', '查看当前租户的正式报价、价格规则快照与有效期。'],
  orders: ['订单管理', '查看当前租户订单，并按订单编号查询详情和处理进度。'],
  warehouse: ['仓库作业', '按订单处理入库、复称、费用确认与出库。'],
  tracking: ['轨迹追踪', '按订单查询物流轨迹与当前状态。'],
  exceptions: ['异常与索赔', '查看异常单及索赔处理结果。'],
  billing: ['账单与对账', '查看账单批次、明细与对账记录。'],
  audit: ['审计日志', '查询当前权限范围内的脱敏操作记录。'],
} as const
const info = computed(() => domainInfo[props.domain])
const quoteColumns = ['quoteNo', 'destinationCountry', 'declaredChargeableWeight', 'amount', 'currency', 'status', 'validTo']
const columns = computed(() => props.domain === 'quotes'
  ? quoteColumns
  : Object.keys(rows.value[0] ?? {}).filter(key => !['detail', 'feeDetail', 'claim'].includes(key)).slice(0, 7))
const currentQuote = computed(() => props.domain === 'quotes' && selected.value ? selected.value as unknown as Quote : undefined)
const canCreateOrder = computed(() => quoteValidation.value?.canCreateOrder === true)
const createOrderLabel = computed(() => canCreateOrder.value ? '创建订单' : quoteValidation.value ? '报价不可创建订单' : '校验后创建订单')
const warehouseStatus = computed(() => String(selected.value?.currentStatus ?? selected.value?.status ?? ''))
const quoteGroups = computed(() => {
  const quote = currentQuote.value
  if (!quote) return []
  const detail: QuoteFeeDetail = quote.feeDetail ?? {}
  return [
    { title: '报价信息', items: [['quoteNo', quote.quoteNo], ['status', quote.status], ['destinationCountry', quote.destinationCountry], ['storeId', quote.storeId], ['channelId', quote.channelId], ['validFrom', quote.validFrom], ['validTo', quote.validTo]] },
    { title: '货物与计费重量', items: [['declaredWeight', quote.declaredWeight], ['declaredLength', quote.declaredLength], ['declaredWidth', quote.declaredWidth], ['declaredHeight', quote.declaredHeight], ['declaredVolumeWeight', quote.declaredVolumeWeight], ['declaredChargeableWeight', quote.declaredChargeableWeight]] },
    { title: '已应用的价格规则', items: [['ruleVersionNo', quote.ruleVersionNo], ['priceRuleId', detail.priceRuleId], ['tierNo', detail.tierNo], ['volumeDivisor', detail.volumeDivisor], ['roundingMode', detail.roundingMode], ['roundingIncrement', detail.roundingIncrement]] },
    { title: '费用结果', items: [['amount', quote.amount], ['currency', quote.currency], ['version', quote.version]] },
  ] as Array<{ title: string; items: Array<[string, unknown]> }>
})

function selectResult(value: unknown) {
  selected.value = value as Record<string, unknown>
  quoteValidation.value = undefined
  if (typeof selected.value.version === 'number') action.version = selected.value.version
}
async function loadPriceConfirmation(order: orders.ShipmentOrder) {
  priceConfirmation.value = undefined
  priceConfirmationError.value = ''
  if (order.status !== 'PENDING_PRICE_CONFIRMATION') return
  try {
    priceConfirmation.value = await orders.getPriceConfirmation(order.id)
    action.version = priceConfirmation.value.version
  } catch (cause) {
    const apiError = toApiError(cause)
    priceConfirmationError.value = getApiErrorMessage(apiError, '费用确认状态加载失败。')
    priceConfirmationErrorCode.value = apiError.code
    priceConfirmationErrorTraceId.value = apiError.traceId
  }
}
function autoLoadsList(domain: typeof props.domain) { return ['quotes', 'orders', 'warehouse', 'exceptions', 'billing', 'audit'].includes(domain) }
function auditPaginationFromRoute() {
  const rawPage = Array.isArray(route.query.page) ? route.query.page[0] : route.query.page
  const rawPageSize = Array.isArray(route.query.pageSize) ? route.query.pageSize[0] : route.query.pageSize
  const parsedPage = Number(rawPage)
  const parsedPageSize = Number(rawPageSize)
  return {
    page: Number.isInteger(parsedPage) && parsedPage > 0 ? parsedPage : 1,
    pageSize: [20, 50, 100].includes(parsedPageSize) ? parsedPageSize : 20,
  }
}
function resetDomainState() {
  requestEpoch.value += 1
  loading.value = false
  error.value = ''
  errorCode.value = undefined
  errorTraceId.value = undefined
  rows.value = []
  quotePage.value = { items: [], page: quoteFilter.page, pageSize: quoteFilter.pageSize, total: 0, totalPages: 0 }
  const auditPagination = auditPaginationFromRoute()
  auditPage.value = { items: [], ...auditPagination, total: 0, totalPages: 0 }
  selected.value = undefined
  quoteValidation.value = undefined
  trackingEvents.value = []
  trackingLoading.value = false
  trackingError.value = ''
  trackingErrorCode.value = undefined
  trackingErrorTraceId.value = undefined
  priceConfirmation.value = undefined
  priceConfirmationError.value = ''
  priceConfirmationErrorCode.value = undefined
  priceConfirmationErrorTraceId.value = undefined
  successMessage.value = ''
  showOrderForm.value = false
  createdOrder.value = undefined
  lookupId.value = ''
  Object.assign(action, { version: 0, trackingNo: '', actualWeight: 0, actualLength: 0, actualWidth: 0, actualHeight: 0 })
}
async function loadList() {
  if (loading.value) return
  const domain = props.domain
  const epoch = ++requestEpoch.value
  loading.value = true
  error.value = ''
  errorCode.value = undefined
  successMessage.value = ''
    selected.value = undefined
    quoteValidation.value = undefined
    trackingEvents.value = []
    trackingError.value = ''
    trackingErrorCode.value = undefined
    trackingErrorTraceId.value = undefined
  try {
    let data: unknown
    if (domain === 'quotes') data = await quotes.listQuotes(toQuoteApiQuery(quoteFilter))
    else if (domain === 'orders') {
      data = await orders.listShipmentOrders(toOrderApiQuery(orderFilter))
    }
    else if (domain === 'warehouse') data = await orders.listShipmentOrders()
    else if (domain === 'exceptions') {
      const filter = exceptionListFilter(route.query)
      data = await exceptions.listExceptionCases({ status: filter.status, workbenchFilter: filter.workbenchFilter })
    }
    else if (domain === 'billing') data = auth.hasPermission('finance:bill-import') ? await billing.listBillImportBatches() : await billing.listReconciliations()
    else if (domain === 'audit') {
      const pagination = auditPaginationFromRoute()
      data = auth.scope === 'PLATFORM'
        ? (tenantId.value ? await audit.listPlatformTenantAuditLogs(tenantId.value, pagination) : { items: [], ...pagination, total: 0, totalPages: 0 })
        : await audit.listTenantAuditLogs(pagination)
    }
    else return
    if (epoch === requestEpoch.value && domain === props.domain) {
      if (domain === 'quotes') quotePage.value = data as ApiPage<Quote>
      if (domain === 'orders') orderPage.value = data as ApiPage<orders.ShipmentOrder>
      if (domain === 'audit') auditPage.value = data as ApiPage<audit.AuditLog>
      rows.value = ((data as { items: Record<string, unknown>[] }).items ?? [])
    }
  } catch (cause) {
    if (epoch === requestEpoch.value && domain === props.domain) {
      const apiError = toApiError(cause)
      error.value = getApiErrorMessage(apiError, '数据加载失败。')
      errorCode.value = apiError.code
      errorTraceId.value = apiError.traceId
    }
  } finally {
    if (epoch === requestEpoch.value) loading.value = false
  }
}
async function lookup() {
  if (!lookupId.value.trim()) return
  if (loading.value) return
  loading.value = true
  error.value = ''
  errorCode.value = undefined
  errorTraceId.value = undefined
  rows.value = []
  quoteValidation.value = undefined
  trackingEvents.value = []
  trackingError.value = ''
  try {
    let data: unknown
    if (props.domain === 'orders' || props.domain === 'warehouse') {
      data = await orders.getOrder(lookupId.value.trim())
      await loadPriceConfirmation(data as orders.ShipmentOrder)
      if (props.domain === 'orders') {
        await loadOrderTracking(lookupId.value.trim())
      }
    }
    else if (props.domain === 'tracking') {
      const page = await tracking.listTrackingEvents(lookupId.value.trim(), { page: 1, pageSize: 50 })
      trackingEvents.value = page.items
      data = { events: page.items, total: page.total }
    }
    else if (props.domain === 'quotes') data = await quotes.getQuote(lookupId.value.trim())
    else if (props.domain === 'exceptions') data = await exceptions.getExceptionCase(lookupId.value.trim())
    else if (props.domain === 'billing') data = auth.hasPermission('finance:bill-import') ? await billing.getBillImportBatch(lookupId.value.trim()) : await billing.getReconciliation(lookupId.value.trim())
    else data = auth.scope === 'PLATFORM' ? await audit.getPlatformTenantAuditLog(tenantId.value, lookupId.value.trim()) : await audit.getTenantAuditLog(lookupId.value.trim())
    selectResult(data)
  } catch (cause) {
    const apiError = toApiError(cause)
    error.value = getApiErrorMessage(apiError, '详情查询失败。')
    errorCode.value = apiError.code
    errorTraceId.value = apiError.traceId
  } finally {
    loading.value = false
  }
}
async function loadOrderTracking(orderId: string) {
  trackingLoading.value = true
  trackingError.value = ''
  trackingErrorCode.value = undefined
  trackingErrorTraceId.value = undefined
  try {
    trackingEvents.value = (await tracking.listTrackingEvents(orderId, { page: 1, pageSize: 50 })).items
  } catch (cause) {
    const apiError = toApiError(cause)
    trackingError.value = getApiErrorMessage(apiError, '订单轨迹加载失败，请稍后重试。')
    trackingErrorCode.value = apiError.code
    trackingErrorTraceId.value = apiError.traceId
  } finally {
    trackingLoading.value = false
  }
}
function retryOrderTracking() { return lookupId.value.trim() ? loadOrderTracking(lookupId.value.trim()) : undefined }
function retryRequest() { return lookupId.value.trim() ? lookup() : loadList() }
async function selectQuote(row: Record<string, unknown>) {
  if (row.id == null) return
  lookupId.value = String(row.id)
  await lookup()
}
async function selectOrder(row: Record<string, unknown>) {
  if (row.id == null) return
  lookupId.value = String(row.id)
  await lookup()
}
async function validateSelectedQuote() {
  const quote = currentQuote.value
  if (!quote) return
  await submit.submit(async () => { quoteValidation.value = await quotes.validateQuote(quote.id) })
}
function validationMessage(validation: QuoteValidation) {
  if (validation.canCreateOrder) return '该报价当前可用于创建订单。'
  return { AVAILABLE: '该报价当前可用于创建订单。', EXPIRED: '报价已过期，请重新获取正式报价。', CANCELLED: '该报价已取消，不能创建订单。', ALREADY_USED: '该报价已用于创建订单。' }[validation.reason]
}
async function openOrderForm() {
  const quote = currentQuote.value
  if (!quote) return
  await submit.submit(async () => {
    const validation = await quotes.validateQuote(quote.id)
    quoteValidation.value = validation
    if (!validation.canCreateOrder) throw new Error(validationMessage(validation))
    orderForm.senderAddress.countryCode = 'CN'
    orderForm.receiverAddress.countryCode = quote.destinationCountry.toUpperCase()
    orderForm.items.forEach(item => { if (!item.currency) item.currency = quote.currency })
    showOrderForm.value = true
  })
}
function addOrderItem() { orderForm.items.push(emptyItem()) }
function removeOrderItem(index: number) { if (orderForm.items.length > 1) orderForm.items.splice(index, 1) }
function validateOrderForm() {
  const addresses = [orderForm.senderAddress, orderForm.receiverAddress]
  if (addresses.some(address => !address.contactName.trim() || !address.phone.trim() || !address.countryCode.trim() || !address.city.trim() || !address.addressLine1.trim() || !address.postalCode.trim())) throw new Error('请完整填写发件人、收件人的必填地址信息。')
  if (addresses.some(address => !countryCodePattern.test(address.countryCode))) throw new Error('国家/地区请填写两位大写 ISO 代码，例如 CN 或 US。')
  if (orderForm.receiverAddress.countryCode !== currentQuote.value?.destinationCountry.toUpperCase()) throw new Error('收件国家/地区必须与报价中的目的国家/地区一致。')
  if (orderForm.items.some(item => !item.sku.trim() || !item.productName.trim() || item.quantity <= 0 || item.unitPrice <= 0 || !item.currency.trim())) throw new Error('请完整填写每个货物的编码、名称、数量、申报单价和币种。')
}
async function createOrderFromQuote() {
  const quote = currentQuote.value
  if (!quote) return
  await submit.submit(async () => {
    validateOrderForm()
    const order = await orders.createShipmentOrderFromQuote(quote.id, {
      senderAddress: { ...orderForm.senderAddress, countryCode: orderForm.senderAddress.countryCode.trim().toUpperCase() },
      receiverAddress: { ...orderForm.receiverAddress, countryCode: orderForm.receiverAddress.countryCode.trim().toUpperCase() },
      items: orderForm.items.map(item => ({ ...item, sku: item.sku.trim(), productName: item.productName.trim(), currency: item.currency.trim().toUpperCase(), hsCode: item.hsCode.trim() || undefined, countryOfOrigin: item.countryOfOrigin.trim().toUpperCase() || undefined }))
    })
    createdOrder.value = order
    showOrderForm.value = false
    quoteValidation.value = { quoteId: quote.id, exists: true, expired: false, canCreateOrder: false, reason: 'ALREADY_USED' }
  })
}
async function runOrderAction(kind: 'submit' | 'cancel' | 'inbound' | 'measure' | 'outbound' | 'price-request' | 'price-confirm') {
  const confirmation = kind === 'submit'
    ? { title: '确认提交订单', description: '提交后订单将进入待入库流程，请确认订单信息已经完整。', confirmLabel: '提交订单' }
    : kind === 'cancel'
      ? { title: '确认取消订单', description: '取消后不能恢复，请确认后继续。', confirmLabel: '取消订单', danger: true }
      : kind === 'outbound'
        ? { title: '确认完成出库', description: '该操作会推进订单状态，请确认物流单号和货物信息无误。', confirmLabel: '确认出库' }
        : kind === 'price-confirm'
          ? { title: '确认最终费用', description: '确认后订单将进入待出库流程，费用结果将被审计记录。', confirmLabel: '确认费用' }
          : undefined
  if (confirmation && !await confirm(confirmation)) return
  await submit.submit(async () => {
    successMessage.value = ''
    const id = lookupId.value.trim()
    let result: unknown
    if (kind === 'submit') result = await orders.submitOrder(id, action.version)
    if (kind === 'cancel') result = await orders.cancelOrder(id, { version: action.version })
    if (kind === 'inbound') result = await warehouse.confirmInbound(id, action.version)
    if (kind === 'measure') result = await warehouse.submitMeasurement(id, { actualWeight: action.actualWeight, actualLength: action.actualLength, actualWidth: action.actualWidth, actualHeight: action.actualHeight, version: action.version })
    if (kind === 'outbound') result = await warehouse.confirmOutbound(id, { trackingNo: action.trackingNo.trim(), version: action.version })
    if (kind === 'price-request' || kind === 'price-confirm') {
      if (!priceConfirmation.value) throw new Error('费用确认调整信息尚未加载。')
      const body = { feeAdjustmentId: priceConfirmation.value.feeAdjustmentId, expectedFee: priceConfirmation.value.afterAmount, version: action.version }
      result = kind === 'price-request' ? await orders.requestPriceConfirmation(id, body) : await orders.confirmPrice(id, body)
      priceConfirmation.value = result as orders.PriceConfirmationView
      action.version = priceConfirmation.value.version
      if (kind === 'price-confirm' && selected.value) {
        selected.value = {
          ...selected.value,
          currentStatus: priceConfirmation.value.currentStatus,
          status: priceConfirmation.value.currentStatus,
          currentFee: priceConfirmation.value.currentFee,
          confirmedFee: priceConfirmation.value.confirmedFee,
          version: priceConfirmation.value.version,
        }
      }
      successMessage.value = kind === 'price-request' ? '费用确认申请已提交，等待财务或租户管理员最终确认。' : '费用已确认，订单已进入待出库流程。'
      return
    }
    if (props.domain === 'warehouse') {
      selectResult(await orders.getOrder(id))
    } else {
      selectResult(result)
    }
    successMessage.value = ({ submit: '订单已提交，已进入待入库流程。', cancel: '订单已取消。', inbound: '订单已完成入库。', measure: '复称已保存，系统已重新计算计费重量和费用。', outbound: '订单已完成出库。' } as const)[kind as 'submit' | 'cancel' | 'inbound' | 'measure' | 'outbound']
  })
}
function format(key: string, value: unknown) {
  if (props.domain === 'quotes' && currentQuote.value && (value == null || value === '')) {
    return ['priceRuleId', 'tierNo', 'volumeDivisor', 'roundingMode', 'roundingIncrement'].includes(key) ? '未配置' : '未提供'
  }
  return displayValue(key, value)
}
function syncQuoteRoute(page = 1) {
  if (props.domain !== 'quotes') return
  quoteFilter.page = page
  void router.replace({ name: 'app-quotes', query: toQuoteRouteQuery(quoteFilter) })
}
function applyQuoteFilters() { syncQuoteRoute(1) }
function clearQuoteFilters() {
  Object.assign(quoteFilter, defaultQuoteFilter)
  void router.replace({ name: 'app-quotes' })
}
function changeQuotePageSize(pageSize: number) { quoteFilter.pageSize = pageSize; syncQuoteRoute(1) }
function syncOrderRoute(page = 1) { if (props.domain !== 'orders') return; orderFilter.page = page; void router.replace({ name: 'app-orders', query: toOrderRouteQuery(orderFilter) }) }
function applyOrderFilters() { syncOrderRoute(1) }
function clearOrderFilters() { Object.assign(orderFilter, defaultOrderFilter); selectedOrderIds.value=[]; void router.replace({ name: 'app-orders' }) }
function changeOrderPageSize(pageSize: number) { orderFilter.pageSize = pageSize; syncOrderRoute(1) }
function syncAuditRoute(page = 1) {
  if (props.domain !== 'audit') return
  void router.replace({ name: 'app-audit', query: { ...route.query, page: String(page), pageSize: String(auditPage.value.pageSize) } })
}
function changeAuditPageSize(pageSize: number) { auditPage.value.pageSize = pageSize; syncAuditRoute(1) }
function toggleAllOrders(event: Event) { selectedOrderIds.value = (event.target as HTMLInputElement).checked ? rows.value.map(row => String(row.id)) : [] }
async function exportOrders(selectedOnly = false) { const response = await orders.exportShipmentOrders({ ...toOrderApiQuery(orderFilter), ids: selectedOnly ? selectedOrderIds.value : undefined }); const url = URL.createObjectURL(response.data); const link = document.createElement('a'); link.href = url; link.download = 'orders.csv'; link.click(); URL.revokeObjectURL(url) }
function startQuoteReuse(mode: 'copy' | 'requote') {
  const quote = currentQuote.value
  if (!quote) return
  void router.push({ name: 'app-quote-create', query: { copyQuoteId: String(quote.id), mode } })
}
function applyRouteFilters() {
  if (props.domain === 'quotes') {
    Object.assign(quoteFilter, parseQuoteQuery(route.query))
    routeFilterNotice.value = ''
    return quoteFilter
  }
  if (props.domain === 'orders') {
    Object.assign(orderFilter, parseOrderQuery(route.query))
    routeFilterNotice.value = ''
    return orderFilter
  }
  if (props.domain === 'exceptions') {
    const filter = exceptionListFilter(route.query)
    routeFilterNotice.value = filter.notice ?? ''
    return filter
  }
  if (props.domain === 'audit') {
    const pagination = auditPaginationFromRoute()
    auditPage.value = { items: [], ...pagination, total: 0, totalPages: 0 }
    routeFilterNotice.value = ''
    return pagination
  }
  routeFilterNotice.value = ''
  return undefined
}
watch(() => [props.domain, route.fullPath], domain => {
  resetDomainState()
  applyRouteFilters()
  if (autoLoadsList(domain[0] as typeof props.domain)) void loadList()
}, { immediate: true })
</script>

<template>
  <section>
    <div class="page-heading"><div><span class="kicker">业务工作台</span><h1>{{ info[0] }}</h1><p>{{ info[1] }}</p></div></div>
    <div class="panel lookup-bar">
      <label v-if="domain === 'audit' && auth.scope === 'PLATFORM'"><span>租户 ID</span><input v-model="tenantId" /></label>
      <label><span>{{ domain === 'audit' ? '日志 ID' : domain === 'billing' ? '批次 ID' : domain === 'exceptions' ? '异常单 ID' : domain === 'quotes' ? '报价 ID' : '订单 ID' }}</span><input v-model="lookupId" @keyup.enter="lookup" /></label>
      <button class="btn btn--primary" :disabled="loading" @click="lookup"><Search :size="16" />查询详情</button>
      <button v-if="['quotes', 'orders', 'warehouse', 'exceptions', 'billing', 'audit'].includes(domain)" class="btn btn--secondary" :disabled="loading" @click="loadList">查看列表</button>
    </div>
    <form v-if="domain === 'quotes'" class="panel quote-filter-bar" @submit.prevent="applyQuoteFilters">
      <label><span>报价单号</span><input v-model.trim="quoteFilter.quoteNo" maxlength="64" /></label>
      <label><span>店铺 ID</span><input v-model.trim="quoteFilter.storeId" inputmode="numeric" /></label>
      <label><span>目的国家/地区</span><input v-model.trim="quoteFilter.destinationCountry" maxlength="2" /></label>
      <label><span>渠道 ID</span><input v-model.trim="quoteFilter.channelId" inputmode="numeric" /></label>
      <label><span>状态</span><select v-model="quoteFilter.status"><option value="">全部</option><option value="VALID">有效</option><option value="EXPIRED">已过期</option><option value="CANCELLED">已取消</option></select></label>
      <label><span>创建时间起</span><input v-model="quoteFilter.createdFrom" type="datetime-local" /></label>
      <label><span>创建时间止</span><input v-model="quoteFilter.createdTo" type="datetime-local" /></label>
      <label><span>有效期起</span><input v-model="quoteFilter.validFrom" type="datetime-local" /></label>
      <label><span>有效期止</span><input v-model="quoteFilter.validTo" type="datetime-local" /></label>
      <label><span>排序字段</span><select v-model="quoteFilter.sortField"><option value="createdAt">创建时间</option><option value="quoteNo">报价单号</option><option value="validTo">有效期</option><option value="amount">金额</option><option value="status">状态</option></select></label>
      <label><span>排序方向</span><select v-model="quoteFilter.sortDirection"><option value="DESC">降序</option><option value="ASC">升序</option></select></label>
      <label><span>每页条数</span><select v-model.number="quoteFilter.pageSize"><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select></label>
      <div class="page-actions"><button class="btn btn--primary" type="submit" :disabled="loading">筛选</button><button class="btn btn--secondary" type="button" :disabled="loading" @click="clearQuoteFilters">重置</button></div>
    </form>
    <form v-if="domain === 'orders'" class="panel quote-filter-bar" @submit.prevent="applyOrderFilters">
      <label><span>订单号</span><input v-model.trim="orderFilter.orderNo" maxlength="64" /></label><label><span>店铺 ID</span><input v-model.number="orderFilter.storeId" type="number" min="1" /></label><label><span>状态</span><select v-model="orderFilter.status"><option :value="undefined">全部</option><option value="DRAFT">草稿</option><option value="PENDING_INBOUND">待入库</option><option value="INBOUND">已入库</option><option value="PENDING_PRICE_CONFIRMATION">待确认费用</option><option value="READY_FOR_OUTBOUND">待出库</option><option value="OUTBOUND">已出库</option><option value="IN_TRANSIT">运输中</option><option value="DELIVERED">已签收</option><option value="CANCELLED">已取消</option></select></label><label><span>目的国家/地区</span><input v-model.trim="orderFilter.destinationCountry" maxlength="2" /></label><label><span>物流渠道 ID</span><input v-model.trim="orderFilter.channelId" inputmode="numeric" /></label><label><span>顺丰/物流单号</span><input v-model.trim="orderFilter.trackingNo" maxlength="128" /></label><label><span>创建时间起</span><input v-model="orderFilter.createdFrom" type="datetime-local" /></label><label><span>创建时间止</span><input v-model="orderFilter.createdTo" type="datetime-local" /></label><label><span>排序字段</span><select v-model="orderFilter.sortBy"><option value="createdAt">创建时间</option><option value="orderNo">订单号</option><option value="status">状态</option><option value="destinationCountry">目的地</option><option value="channelId">渠道</option><option value="estimatedFee">费用</option></select></label><label><span>排序方向</span><select v-model="orderFilter.sortDirection"><option value="DESC">降序</option><option value="ASC">升序</option></select></label><label><span>每页条数</span><select v-model.number="orderFilter.pageSize"><option :value="20">20</option><option :value="50">50</option><option :value="100">100</option></select></label><div class="page-actions"><button class="btn btn--primary" type="submit" :disabled="loading">筛选</button><button class="btn btn--secondary" type="button" :disabled="loading" @click="clearOrderFilters">重置</button><button class="btn btn--secondary" type="button" :disabled="loading" @click="exportOrders(false)">导出筛选结果</button><button class="btn btn--secondary" type="button" :disabled="loading || !selectedOrderIds.length" @click="exportOrders(true)">导出已选</button></div>
    </form>
    <div v-if="routeFilterNotice" class="alert alert--warning" role="status">{{ routeFilterNotice }}</div>
    <div v-if="(domain === 'orders' || domain === 'warehouse') && selected && warehouseStatus === 'PENDING_PRICE_CONFIRMATION' && priceConfirmation" class="panel action-panel">
      <div><strong>费用确认</strong><p>当前费用：{{ priceConfirmation.currency }} {{ priceConfirmation.afterAmount }}；差额：{{ priceConfirmation.currency }} {{ priceConfirmation.differenceAmount }}。</p><small>费用确认状态：{{ format('confirmationStatus', priceConfirmation.confirmationStatus) }}；时间按 Asia/Shanghai 展示。</small></div>
      <div class="page-actions">
        <button v-if="auth.hasRole('MERCHANT_OPERATOR') && auth.hasPermission('order:price-request')" class="btn btn--secondary" :disabled="submit.submitting.value || priceConfirmation.confirmationStatus !== 'PENDING_CONFIRMATION'" @click="runOrderAction('price-request')">提交费用确认申请</button>
        <button v-if="(auth.hasRole('FINANCE_OPERATOR') || auth.hasRole('MERCHANT_ADMIN')) && auth.hasPermission('order:price-confirm')" class="btn btn--primary" :disabled="submit.submitting.value || !['PENDING_CONFIRMATION', 'REQUESTED'].includes(priceConfirmation.confirmationStatus)" @click="runOrderAction('price-confirm')">最终确认费用</button>
      </div>
    </div>
    <div v-if="priceConfirmationError" class="alert alert--error" role="alert"><b v-if="priceConfirmationErrorCode">{{ priceConfirmationErrorCode }}：</b>{{ priceConfirmationError }} <span v-if="priceConfirmationErrorTraceId">追踪编号：{{ priceConfirmationErrorTraceId }}</span></div>
    <div v-if="domain === 'warehouse' && selected" class="panel action-panel"><label><span>版本</span><input v-model.number="action.version" type="number" min="0" /></label><label><span>实际重量 kg</span><input v-model.number="action.actualWeight" type="number" step="0.001" /></label><label><span>长 / 宽 / 高 cm</span><span class="inline-inputs"><input v-model.number="action.actualLength" type="number" /><input v-model.number="action.actualWidth" type="number" /><input v-model.number="action.actualHeight" type="number" /></span></label><label><span>物流单号</span><input v-model.trim="action.trackingNo" /></label><div class="page-actions"><button class="btn btn--secondary" :disabled="submit.submitting.value || warehouseStatus !== 'PENDING_INBOUND'" @click="runOrderAction('inbound')">确认入库</button><button class="btn btn--secondary" :disabled="submit.submitting.value || warehouseStatus !== 'INBOUND'" @click="runOrderAction('measure')">提交复称</button><button class="btn btn--primary" :disabled="submit.submitting.value || warehouseStatus !== 'READY_FOR_OUTBOUND' || !action.trackingNo.trim()" @click="runOrderAction('outbound')">确认出库</button></div></div>
    <div v-if="domain === 'orders' && selected && auth.hasPermission('order:manage')" class="page-actions action-row"><button class="btn btn--primary" :disabled="submit.submitting.value" @click="runOrderAction('submit')">提交订单</button><button class="btn btn--secondary" :disabled="submit.submitting.value" @click="runOrderAction('cancel')">取消订单</button></div>
    <div v-if="(domain === 'orders' || domain === 'tracking') && selected" class="panel tracking-panel">
      <div class="page-heading"><div><h2>订单轨迹</h2><p>仅展示当前租户可见的物流事件，不包含物流商原始回调内容。</p></div></div>
      <DataState :loading="trackingLoading" :error="trackingError" :trace-id="trackingErrorTraceId" :retry="retryOrderTracking" :empty="!trackingEvents.length" empty-title="暂无轨迹事件">
        <div class="data-table-wrap"><table class="data-table"><thead><tr><th>时间</th><th>物流单号</th><th>事件编号</th><th>事件类型</th><th>说明</th><th>处理状态</th></tr></thead><tbody><tr v-for="event in trackingEvents" :key="event.id"><td>{{ format('eventTime', event.eventTime) }}</td><td>{{ event.trackingNo }} <CopyTextButton v-if="event.trackingNo" :value="event.trackingNo" label="物流单号" /></td><td>{{ event.eventId }}</td><td>{{ event.eventCode }}</td><td>{{ event.eventDescription || '-' }}</td><td><StatusBadge :status="event.processStatus" :label="format('processStatus', event.processStatus)" /></td></tr></tbody></table></div>
      </DataState>
    </div>
    <ActionError :message="submit.errorMessage.value" :code="submit.errorCode.value" :trace-id="submit.errorTraceId.value" />
    <div v-if="successMessage" class="alert alert--success" role="status">{{ successMessage }}</div>
    <DataState :loading="loading" :error="error" :error-code="errorCode" :trace-id="errorTraceId" :retry="retryRequest" :empty="!rows.length && !selected">
      <div v-if="currentQuote" class="page-actions quote-reuse-actions"><button class="btn btn--secondary" type="button" :disabled="submit.submitting.value" @click="startQuoteReuse('copy')">复制报价</button><button class="btn btn--secondary" type="button" :disabled="submit.submitting.value" @click="startQuoteReuse('requote')">重新报价</button></div>
      <div v-if="currentQuote" class="quote-detail">
        <div class="panel quote-detail__hero"><div><span class="kicker">正式报价</span><h2>{{ currentQuote.quoteNo }} <CopyTextButton :value="currentQuote.quoteNo" label="报价号" /></h2><p>以下费用和计算依据均为创建报价时锁定的规则快照。</p></div><div class="quote-detail__amount"><small>报价金额</small><strong>{{ currentQuote.currency }} {{ currentQuote.amount }}</strong><StatusBadge :status="currentQuote.status" :label="format('status', currentQuote.status)" /></div></div>
        <div class="quote-detail__groups"><section v-for="group in quoteGroups" :key="group.title" class="panel quote-detail__group"><h2>{{ group.title }}</h2><dl><div v-for="([key, value]) in group.items" :key="key"><dt>{{ displayLabel(key) }}</dt><dd>{{ format(key, value) }}</dd></div></dl></section></div>
        <div class="panel quote-detail__validation"><div><CheckCircle2 :size="20" /><div><h2>报价可用性</h2><p v-if="!quoteValidation">确认报价是否仍可用于创建订单。</p><p v-else>{{ validationMessage(quoteValidation) }}</p></div></div><div class="page-actions"><button class="btn btn--secondary" :disabled="submit.submitting.value" @click="validateSelectedQuote">校验报价</button><button v-if="auth.hasPermission('order:create')" class="btn" :class="canCreateOrder ? 'btn--primary' : 'btn--secondary'" :disabled="submit.submitting.value || quoteValidation?.canCreateOrder === false" :title="quoteValidation?.canCreateOrder === false ? validationMessage(quoteValidation) : undefined" @click="openOrderForm">{{ createOrderLabel }}</button></div></div>
        <form v-if="showOrderForm" class="panel quote-order-form" @submit.prevent="createOrderFromQuote">
          <div class="quote-order-form__heading"><div><span class="kicker">从报价创建订单</span><h2>补充交付信息</h2><p>报价金额、计费重量与物流渠道将保持不变。</p></div><button class="btn btn--secondary" type="button" @click="showOrderForm = false">取消</button></div>
          <div class="quote-order-form__addresses">
            <fieldset><legend>发件人信息</legend><div class="form-grid"><label><span>发件国家/地区</span><select v-model="orderForm.senderAddress.countryCode" disabled required><option value="CN">中国（CN）</option></select></label><label><span>联系人</span><input v-model.trim="orderForm.senderAddress.contactName" required /></label><label><span>联系电话</span><input v-model.trim="orderForm.senderAddress.phone" required /></label><label><span>公司名称（选填）</span><input v-model.trim="orderForm.senderAddress.companyName" /></label><label><span>邮箱（选填）</span><input v-model.trim="orderForm.senderAddress.email" type="email" /></label><label><span>城市</span><input v-model.trim="orderForm.senderAddress.city" required /></label><label><span>地址</span><input v-model.trim="orderForm.senderAddress.addressLine1" required /></label><label><span>邮编</span><input v-model.trim="orderForm.senderAddress.postalCode" required /></label></div></fieldset>
            <fieldset><legend>收件人信息</legend><div class="form-grid"><label><span>目的国家/地区</span><select v-model="orderForm.receiverAddress.countryCode" disabled required><option :value="orderForm.receiverAddress.countryCode">{{ destinationCountryLabel }}</option></select></label><label><span>联系人</span><input v-model.trim="orderForm.receiverAddress.contactName" required /></label><label><span>联系电话</span><input v-model.trim="orderForm.receiverAddress.phone" required /></label><label><span>公司名称（选填）</span><input v-model.trim="orderForm.receiverAddress.companyName" /></label><label><span>邮箱（选填）</span><input v-model.trim="orderForm.receiverAddress.email" type="email" /></label><label><span>城市</span><input v-model.trim="orderForm.receiverAddress.city" required /></label><label><span>地址</span><input v-model.trim="orderForm.receiverAddress.addressLine1" required /></label><label><span>邮编</span><input v-model.trim="orderForm.receiverAddress.postalCode" required /></label></div></fieldset>
          </div>
          <section class="quote-order-form__items"><div class="quote-order-form__items-heading"><h2>货物明细</h2><button class="btn btn--secondary" type="button" @click="addOrderItem"><Plus :size="16" />新增货物</button></div><div v-for="(item, index) in orderForm.items" :key="index" class="quote-order-form__item"><div class="form-grid"><label><span>SKU</span><input v-model.trim="item.sku" required /></label><label><span>货物名称</span><input v-model.trim="item.productName" required /></label><label><span>数量</span><input v-model.number="item.quantity" min="0.001" step="0.001" type="number" required /></label><label><span>申报单价</span><input v-model.number="item.unitPrice" min="0.01" step="0.01" type="number" required /></label><label><span>币种</span><input v-model.trim="item.currency" maxlength="3" required /></label><label><span>HS 编码（选填）</span><input v-model.trim="item.hsCode" /></label><label><span>原产国/地区（选填）</span><input v-model.trim="item.countryOfOrigin" maxlength="2" placeholder="CN" /></label></div><button v-if="orderForm.items.length > 1" class="quote-order-form__remove" type="button" :aria-label="`删除货物 ${index + 1}`" @click="removeOrderItem(index)"><Trash2 :size="16" /></button></div></section>
          <div class="form-actions"><span>创建后将生成当前租户的草稿订单。</span><button class="btn btn--primary" :disabled="submit.submitting.value" type="submit">确认创建订单</button></div>
        </form>
        <div v-if="createdOrder" class="panel quote-order-created"><CheckCircle2 :size="20" /><div><h2>订单已创建</h2><p>订单编号：{{ createdOrder.orderNo }} <CopyTextButton :value="createdOrder.orderNo" label="订单号" />，当前为草稿状态。</p></div><RouterLink class="btn btn--primary" :to="`/app/orders?orderId=${createdOrder.id}`">查看订单</RouterLink></div>
      </div>
      <div v-else-if="selected" class="panel detail-grid"><div v-for="(value, key) in selected" :key="key"><small>{{ displayLabel(String(key)) }}</small><span><StatusBadge v-if="String(key).toLowerCase().includes('status')" :status="String(value ?? '')" :label="format(String(key), value)" /><template v-else>{{ format(String(key), value) }}</template><CopyTextButton v-if="['orderNo', 'quoteNo', 'exceptionNo', 'traceId', 'requestId'].includes(String(key)) && value" :value="String(value)" :label="displayLabel(String(key))" /></span></div></div>
      <div v-else class="panel table-panel"><div class="data-table-wrap"><table class="data-table"><thead><tr><th v-if="domain === 'orders'"><input type="checkbox" :checked="rows.length > 0 && selectedOrderIds.length === rows.length" aria-label="全选订单" @change="toggleAllOrders" /></th><th v-for="key in columns" :key="key">{{ displayLabel(key) }}</th><th v-if="['quotes', 'orders', 'warehouse'].includes(domain)">操作</th></tr></thead><tbody><tr v-for="row in rows" :key="String(row.id)"><td v-if="domain === 'orders'"><input v-model="selectedOrderIds" type="checkbox" :value="String(row.id)" :aria-label="`选择订单 ${row.orderNo ?? row.id}`" /></td><td v-for="key in columns" :key="key"><StatusBadge v-if="key.toLowerCase().includes('status')" :status="String(row[key] ?? '')" :label="format(key, row[key])" /><template v-else>{{ format(key, row[key]) }}</template><CopyTextButton v-if="['orderNo', 'quoteNo', 'exceptionNo', 'traceId', 'requestId'].includes(key) && row[key]" :value="String(row[key])" :label="displayLabel(key)" /></td><td v-if="domain === 'quotes'"><button class="table-link" type="button" @click="selectQuote(row)">查看详情<ChevronRight :size="15" /></button></td><td v-else-if="domain === 'orders' || domain === 'warehouse'"><button class="table-link" type="button" @click="selectOrder(row)">查看详情<ChevronRight :size="15" /></button></td></tr></tbody></table></div></div>
    </DataState>
    <ListPagination v-if="domain === 'orders' && !selected" :page="orderPage.page" :page-size="orderPage.pageSize" :total="orderPage.total" :total-pages="orderPage.totalPages" :loading="loading" @update:page="syncOrderRoute" @update:page-size="changeOrderPageSize" />
    <ListPagination v-if="domain === 'quotes' && !selected" :page="quotePage.page" :page-size="quotePage.pageSize" :total="quotePage.total" :total-pages="quotePage.totalPages" :loading="loading" @update:page="syncQuoteRoute" @update:page-size="changeQuotePageSize" />
    <ListPagination v-if="domain === 'audit' && !selected" :page="auditPage.page" :page-size="auditPage.pageSize" :total="auditPage.total" :total-pages="auditPage.totalPages" :loading="loading" @update:page="syncAuditRoute" @update:page-size="changeAuditPageSize" />
  </section>
</template>
