import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import type { ExceptionStatus } from '@/services/exceptions'
import type { OrderStatus } from '@/services/orders'
import type { WarehouseWorkStatus } from '@/services/warehouse'

type QueryValue = string | null | undefined | Array<string | null>

const routes = new Set(['/app/orders', '/app/warehouse', '/app/tracking', '/app/exceptions', '/app/billing'])
const metricRoute = /^\/app\/workbench\/metrics\/[A-Z_]+$/
const orderStatuses = new Set<OrderStatus>(['DRAFT', 'PENDING_INBOUND', 'INBOUND', 'PENDING_PRICE_CONFIRMATION', 'READY_FOR_OUTBOUND', 'OUTBOUND', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED', 'RETURNED', 'LOST'])
const warehouseStatuses = new Set<WarehouseWorkStatus>(['PENDING_INBOUND', 'INBOUND', 'PENDING_PRICE_CONFIRMATION', 'READY_FOR_OUTBOUND', 'OUTBOUND', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED', 'RETURNED', 'LOST'])
const exceptionStatuses = new Set<ExceptionStatus>(['OPEN', 'PROCESSING', 'WAITING_PROVIDER_FEEDBACK', 'RESOLVED', 'PENDING_FINANCE_CONFIRMATION', 'CLOSED'])
const reconciliationStatuses = new Set(['AUTO_CLOSED', 'PENDING_CONFIRMATION', 'CONFIRMED', 'REJECTED'])
const billDetailStatuses = new Set(['IMPORTED', 'MATCHED', 'ERROR'])
const financeTabs = new Set(['batches', 'details', 'reconciliations', 'audit'])
const safeTargetKeys = new Set(['resourceType', 'metricKey', 'status', 'storeId', 'orderId', 'reference', 'resourceId', 'tab', 'timeRange', 'from', 'to'])

function scalar(value: QueryValue) {
  return typeof value === 'string' ? value.trim() : ''
}

function positiveId(value: QueryValue) {
  const normalized = scalar(value)
  return /^\d+$/.test(normalized) && Number(normalized) > 0 ? normalized : undefined
}

export function workbenchTargetLocation(target?: { route: string; query?: Record<string, unknown> }) {
  if (!target || (!routes.has(target.route) && !metricRoute.test(target.route))) return undefined
  const query: LocationQueryRaw = {}
  for (const [key, value] of Object.entries(target.query ?? {})) {
    if (!safeTargetKeys.has(key) || value == null || value === '') continue
    if (typeof value === 'string' || typeof value === 'number') query[key] = String(value)
  }
  return { path: target.route, query }
}

export interface OrderListFilter {
  status?: OrderStatus
  storeId?: string
  lookupId?: string
  workbenchFilter?: 'PENDING_FEE_CONFIRMATION' | 'MISSING_ADDRESS'
  notice?: string
}

export function orderListFilter(query: LocationQuery): OrderListFilter {
  const lookupId = positiveId(query.orderId) ?? positiveId(query.resourceId)
  if (lookupId) return { lookupId }
  const status = scalar(query.status)
  if (orderStatuses.has(status as OrderStatus)) return { status: status as OrderStatus, storeId: positiveId(query.storeId) }
  if (status === 'PENDING_FEE_CONFIRMATION') return { workbenchFilter: 'PENDING_FEE_CONFIRMATION', storeId: positiveId(query.storeId) }
  if (status === 'MISSING_ADDRESS') return { workbenchFilter: 'MISSING_ADDRESS', storeId: positiveId(query.storeId) }
  if (status || scalar(query.resourceType) === 'SHIPMENT_ORDER') {
    return { storeId: positiveId(query.storeId), notice: '当前工作台筛选暂不能由订单列表精确表达，已保留入口条件；列表仅显示当前授权范围内的真实订单。' }
  }
  return { storeId: positiveId(query.storeId) }
}

export interface WarehouseListFilter {
  status?: WarehouseWorkStatus
  notice?: string
}

export function warehouseListFilter(query: LocationQuery): WarehouseListFilter {
  const status = scalar(query.status)
  if (warehouseStatuses.has(status as WarehouseWorkStatus)) return { status: status as WarehouseWorkStatus }
  if (status) return { notice: '当前工作台筛选依赖面单或清关资料状态，仓库列表接口尚未提供等价筛选，未对真实列表进行前端过滤。' }
  return {}
}

export interface TrackingFilter {
  reference?: string
  notice?: string
}

export function trackingFilter(query: LocationQuery): TrackingFilter {
  const reference = scalar(query.reference)
  if (reference) return { reference }
  if (scalar(query.status) || scalar(query.resourceType)) {
    return { notice: '当前轨迹页面仅支持按订单号或顺丰单号查询；轨迹状态聚合列表接口尚未提供，未伪造筛选结果。' }
  }
  return {}
}

export interface ExceptionListFilter {
  status?: ExceptionStatus
  workbenchFilter?: 'PENDING_FOLLOW_UP'
  notice?: string
}

export function exceptionListFilter(query: LocationQuery): ExceptionListFilter {
  const status = scalar(query.status)
  if (exceptionStatuses.has(status as ExceptionStatus)) return { status: status as ExceptionStatus }
  if (status === 'PENDING_FINANCE_REVIEW') return { status: 'PENDING_FINANCE_CONFIRMATION' }
  if (status === 'PENDING_EXCEPTION_FOLLOW_UP') return { workbenchFilter: 'PENDING_FOLLOW_UP' }
  if (status) return { notice: '当前待办状态没有对应的异常列表状态枚举，未使用前端推断筛选。' }
  return {}
}

export interface FinanceListFilter {
  tab?: 'batches' | 'details' | 'reconciliations' | 'audit'
  status?: string
  page: number
  pageSize: number
  recordId?: string
  notice?: string
}

export function financeListFilter(query: LocationQuery): FinanceListFilter {
  const requestedTab = scalar(query.tab)
  const status = scalar(query.status)
  const page = Number(positiveId(query.page) ?? 1)
  const requestedPageSize = Number(positiveId(query.pageSize) ?? 20)
  const pageSize = [20, 50, 100].includes(requestedPageSize) ? requestedPageSize : 20
  const recordId = positiveId(query.recordId)
  const base = { page, pageSize, recordId }
  if (requestedTab && financeTabs.has(requestedTab)) {
    if ((requestedTab === 'reconciliations' && reconciliationStatuses.has(status))
      || (requestedTab === 'details' && billDetailStatuses.has(status))
      || (requestedTab === 'batches' && ['PROCESSING', 'PARTIAL_SUCCESS', 'SUCCESS', 'FAILED'].includes(status))
      || !status) return { ...base, tab: requestedTab as FinanceListFilter['tab'], status: status || undefined }
  }
  if (status === 'PENDING_RECONCILIATION' || status === 'RECONCILIATION_DIFFERENCE') return { ...base, tab: 'reconciliations', status: 'PENDING_CONFIRMATION' }
  if (status === 'BILL_IMPORT_ERRORS') return { ...base, tab: 'details', status: 'ERROR' }
  if (status || scalar(query.resourceType) === 'FINANCE') {
    return { ...base, notice: '当前财务聚合指标包含多个真实列表来源，请从账单批次、账单明细或费用对账标签继续查看。' }
  }
  return base
}
