import { getApiErrorMessage, toApiError } from '@/services/http'
import { getOperationsWorkbench, type OperationsTarget, type OperationsWorkbench, type OperationsWorkbenchQuery } from '@/services/operations'
import { workbenchTargetLocation as toWorkbenchTargetLocation } from './workbenchTargetFilters'

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

export function workbenchStatusLabel(value?: string | null) {
  return ({
    DRAFT: '草稿', PENDING_INBOUND: '待入库', INBOUND: '已入库', PENDING_PRICE_CONFIRMATION: '待确认费用',
    READY_FOR_OUTBOUND: '待出库', OUTBOUND: '已出库', IN_TRANSIT: '在途', DELIVERED: '已签收',
    CANCELLED: '已取消', RETURNED: '已退回', LOST: '遗失', LABEL_READY: '面单已就绪',
  } as Record<string, string>)[String(value)] ?? value ?? '—'
}

export function formatWeightKg(value: number | string | null | undefined) {
  if (value == null || value === '') return '—'
  const parsed = Number(value)
  return Number.isFinite(parsed) ? `${parsed.toFixed(3)} kg` : String(value)
}

export function formatMoney(value: number | string | null | undefined, currency?: string | null) {
  if (value == null || value === '') return '—'
  const parsed = Number(value)
  const amount = Number.isFinite(parsed) ? parsed.toFixed(2) : String(value)
  return currency ? `${currency} ${amount}` : amount
}
