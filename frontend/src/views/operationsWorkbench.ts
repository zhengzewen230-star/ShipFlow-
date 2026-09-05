import { getApiErrorMessage, toApiError } from '@/services/http'
import { getOperationsWorkbench, type OperationsTarget, type OperationsWorkbench, type OperationsWorkbenchQuery } from '@/services/operations'
import { workbenchTargetLocation as toWorkbenchTargetLocation } from './workbenchTargetFilters'
import { displayValue, formatMoney as formatDisplayMoney } from '@/utils/display'

export type WorkbenchErrorKind = 'unauthorized' | 'forbidden' | 'server' | 'network' | 'unknown'

export function classifyWorkbenchError(error: unknown) {
  const apiError = toApiError(error)
  const kind: WorkbenchErrorKind = apiError.status === 401 ? 'unauthorized'
    : apiError.status === 403 ? 'forbidden'
      : apiError.status != null && apiError.status >= 500 ? 'server'
        : apiError.status == null ? 'network' : 'unknown'
  return {
    kind,
    status: apiError.status,
    traceId: apiError.traceId,
    message: getApiErrorMessage(apiError, '运营概览加载失败，请重试。'),
  }
}

export async function copyWorkbenchTraceId(traceId?: string, writeText: (value: string) => Promise<void> = value => navigator.clipboard.writeText(value)) {
  if (!traceId?.trim()) return false
  try {
    await writeText(traceId)
    return true
  } catch {
    return false
  }
}

export function createOperationsWorkbenchLoader(fetcher: (query?: OperationsWorkbenchQuery) => Promise<OperationsWorkbench> = getOperationsWorkbench) {
  let inFlight: Promise<OperationsWorkbench> | undefined
  return {
    load(query: OperationsWorkbenchQuery = {}) {
      if (inFlight) return inFlight
      inFlight = fetcher(query).finally(() => { inFlight = undefined })
      return inFlight
    },
  }
}

export function isOperationsWorkbenchEmpty(data?: OperationsWorkbench) {
  return Boolean(data && data.metrics.every(item => item.count === 0)
    && data.todos.every(item => item.count === 0)
    && data.recentOrders.length === 0 && data.risks.length === 0)
}

export function workbenchTargetLocation(target?: OperationsTarget) {
  return toWorkbenchTargetLocation(target)
}

export function formatShanghaiDateTime(value?: string | null) {
  if (!value) return '—'
  const date = new Date(value)
  if (Number.isNaN(date.getTime())) return value
  return new Intl.DateTimeFormat('zh-CN', {
    timeZone: 'Asia/Shanghai', year: 'numeric', month: '2-digit', day: '2-digit',
    hour: '2-digit', minute: '2-digit', second: '2-digit', hour12: false,
  }).format(date).replace(/\//g, '-')
}

export function formatShanghaiWindow(window?: { from: string; to: string }) {
  if (!window) return '—'
  return `${formatShanghaiDateTime(window.from)} 至 ${formatShanghaiDateTime(window.to)}`
}

export function workbenchStatusLabel(value?: string | null) { return displayValue('status', value) }

const orderCountMetricKeys = new Set([
  'PENDING_ORDERS', 'PENDING_INBOUND', 'PENDING_MEASUREMENT', 'PENDING_LABEL',
  'PENDING_OUTBOUND', 'IN_TRANSIT', 'TRACKING_EXCEPTION',
])

export function metricUnitLabel(metric: { key: string; unit?: string | null }) {
  return metric.unit === 'ORDER_COUNT' || (!metric.unit && orderCountMetricKeys.has(metric.key)) ? '订单数' : '任务数'
}

export function formatWeightKg(value: number | string | null | undefined) {
  if (value == null || value === '') return '—'
  const parsed = Number(value)
  return Number.isFinite(parsed) ? `${parsed.toFixed(3)} kg` : String(value)
}

export function formatMoney(value: number | string | null | undefined, currency?: string | null) {
  return formatDisplayMoney(value, currency)
}
