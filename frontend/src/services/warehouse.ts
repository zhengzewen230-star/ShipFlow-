import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { Id } from '@/types/api'
import type { ApiPage, PageQuery } from '@/types/api'
export interface WarehouseResult { orderId: Id; status: string; chargeableWeight: number; currentFee: number; version: number }
export interface WarehouseOverview {
  pendingInbound: number
  pendingMeasurement: number
  pendingLabel: number
  pendingHandover: number
  pendingOutbound: number
  inTransit: number
  trackingExceptions: number
  todayInbound: number
  todayOutbound: number
  recentOrders: Array<{ id: Id; orderNo: string; trackingNo?: string | null; status: string; destinationCountry?: string | null; chargeableWeight?: number | null; action?: string | null; createdAt: string }>
  recentTrackingExceptions: Array<{ id: Id; orderId: Id; orderNo?: string | null; trackingNo?: string | null; exceptionNo: string; status: string; description: string; reportedAt: string }>
}
export interface MeasurementRequest { actualWeight: number; actualLength: number; actualWidth: number; actualHeight: number; version: number }
export interface OutboundRequest { trackingNo: string; remark?: string; version: number }
export type WarehouseWorkStatus = 'PENDING_INBOUND' | 'INBOUND' | 'PENDING_PRICE_CONFIRMATION' | 'READY_FOR_OUTBOUND' | 'OUTBOUND' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED' | 'RETURNED' | 'LOST'
export type SfOperation = 'CREATE_ORDER' | 'PRINT_ORDER' | 'QUERY_ORDER' | 'CANCEL_ORDER' | 'UPLOAD_CERTIFY'
export interface SfOperationResponse { operation: SfOperation; serviceCode: string; requestId: string; status: string; errorCode?: string | null; errorMessage?: string | null; externalOrderNo?: string | null; trackingNo?: string | null; labelUrl?: string | null; invoiceUrl?: string | null }
export interface WarehouseWorkItem {
  id: Id
  businessOrderNo: string
  sfTrackingNo?: string | null
  tenantId: Id
  tenantName: string
  destinationCountry: string
  declaredWeight: number
  declaredLength: number
  declaredWidth: number
  declaredHeight: number
  declaredVolumeWeight: number
  actualWeight?: number | null
  actualLength?: number | null
  actualWidth?: number | null
  actualHeight?: number | null
  actualVolumeWeight?: number | null
  chargeableWeight: number
  estimatedFee: number
  currentFee: number
  currency: string
  feeDifference: number
  feeAlert: boolean
  warehouseStatus: string
  logisticsStatus: WarehouseWorkStatus
  version: number
  outboundAt?: string | null
  outboundBy?: Id | null
  createdAt: string
}
export interface WarehouseWorkQuery extends PageQuery { status?: WarehouseWorkStatus; orderNo?: string }
export const confirmInbound = async (orderId: Id, version: number) => unwrap<WarehouseResult>(await http.post(`/orders/${orderId}/inbound`, { version }, writeConfig('inbound')))
export const submitMeasurement = async (orderId: Id, body: MeasurementRequest) => unwrap<WarehouseResult>(await http.post(`/orders/${orderId}/measurements`, body, writeConfig('measurement')))
export const confirmOutbound = async (orderId: Id, body: OutboundRequest) => unwrap<WarehouseResult>(await http.post(`/orders/${orderId}/outbound`, body, writeConfig('outbound')))
export const getWarehouseOverview = async () => unwrap<WarehouseOverview>(await http.get('/warehouse/overview'))
export const listWarehouseWork = async (params: WarehouseWorkQuery = {}) => unwrap<ApiPage<WarehouseWorkItem>>(await http.get('/warehouse/orders', { params }))
export const getWarehouseWorkItem = async (id: Id) => unwrap<WarehouseWorkItem>(await http.get(`/warehouse/orders/${id}`))
export const executeSfOperation = async (orderId: Id, operation: SfOperation, msgData = '') => unwrap<SfOperationResponse>(await http.post(`/orders/${orderId}/sf-international/${operation}`, { msgData }, writeConfig(`sf-${operation.toLowerCase()}`)))
export function sfOperationLabel(operation: SfOperation) {
  return ({ CREATE_ORDER: '顺丰订单创建', PRINT_ORDER: '顺丰面单获取', QUERY_ORDER: '顺丰订单查询', CANCEL_ORDER: '顺丰订单取消', UPLOAD_CERTIFY: '清关资料上传' } as const)[operation]
}
