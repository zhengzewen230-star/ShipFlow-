import { http, unwrap } from './http'
import type { PageQuery } from '@/types/api'

export interface OperationsSummary { draft?: number; pendingInbound?: number; inbound?: number; pendingPriceConfirmation?: number; readyForOutbound?: number; outbound?: number; inTransit?: number; delivered?: number; exception?: number; cancelled?: number }
export interface OperationsTodos { pendingPriceConfirmation?: number; pendingReconciliation?: number; activeExceptions?: number; submittedClaims?: number }
export const getOperationsSummary = async () => unwrap<OperationsSummary>(await http.get('/operations/summary'))
export const getOperationsTodos = async () => unwrap<OperationsTodos>(await http.get('/operations/todos'))

export type OperationsTimeRange = 'TODAY' | 'LAST_7_DAYS' | 'LAST_30_DAYS' | 'CUSTOM'
export interface OperationsWorkbenchQuery extends PageQuery {
  timeRange?: OperationsTimeRange
  from?: string
  to?: string
  storeId?: string | number
  sortBy?: 'updatedAt' | 'createdAt'
  sortDirection?: 'ASC' | 'DESC'
  recentLimit?: number
  riskLimit?: number
}
export interface OperationsTarget {
  route: string
  query: Record<string, unknown>
  resourceType: string
}
export interface OperationsTimeWindow {
  preset: OperationsTimeRange
  from: string
  to: string
}
export interface OperationsMetricBreakdown {
  key: string
  label: string
  count: number
  target: OperationsTarget
}
export interface OperationsMetric {
  key: string
  label: string
  count: number
  window: OperationsTimeWindow
  refreshedAt: string
  target: OperationsTarget
  breakdown: OperationsMetricBreakdown[]
  definition?: string | null
  dataSource?: string | null
  timeField?: string | null
  unit?: 'TASK_COUNT' | 'ORDER_COUNT' | string | null
}
export interface OperationsTodo {
  key: string
  label: string
  count: number
  window: OperationsTimeWindow
  target: OperationsTarget
}
export interface OperationsRecentOrder {
  orderId: number | string
  orderNo: string
  storeId: number | string
  storeName: string
  destination: string
  orderStatus: string
  labelStatus?: string | null
  chargeableWeight: number | string
  estimatedFee: number | string
  currency: string
  sfTrackingNo?: string | null
  updatedAt: string
  nextAction: string
  target: OperationsTarget
}
export interface OperationsRisk {
  id: string
  type: string
  level: string
  title: string
  resourceType: string
  resourceId: number | string
  storeId: number | string
  occurredAt: string
  description: string
  target: OperationsTarget
}
export interface OperationsWorkbench {
  businessTimeZone: 'Asia/Shanghai'
  scope: { tenantId: number | string; storeIds: Array<number | string>; scopeType: 'ALL_TENANT_STORES' | 'AUTHORIZED_STORES' }
  timeRange: OperationsTimeWindow
  refreshedAt: string
  metrics: OperationsMetric[]
  todos: OperationsTodo[]
  recentOrders: OperationsRecentOrder[]
  risks: OperationsRisk[]
}
export const getOperationsWorkbench = async (params: OperationsWorkbenchQuery = {}) =>
  unwrap<OperationsWorkbench>(await http.get('/operations/workbench', { params }))

export interface OperationsMetricDrilldownItem {
  itemId: string
  resourceType: string
  resourceId?: number | string | null
  orderId?: number | string | null
  storeId?: number | string | null
  displayNo?: string | null
  status?: string | null
  source: string
  occurredAt?: string | null
}
export interface OperationsMetricDrilldownPage {
  metricKey: string
  metricLabel: string
  definition: string
  dataSource: string
  timeField: string
  unit: 'ORDER_COUNT' | 'TASK_COUNT' | string
  timeRange: OperationsTimeWindow
  page: number
  pageSize: number
  total: number
  totalPages: number
  items: OperationsMetricDrilldownItem[]
}
export const getOperationsMetricDrilldown = async (metricKey: string, params: OperationsWorkbenchQuery = {}) =>
  unwrap<OperationsMetricDrilldownPage>(await http.get(`/operations/workbench/metrics/${encodeURIComponent(metricKey)}/items`, { params }))
