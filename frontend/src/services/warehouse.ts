import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { Id } from '@/types/api'
import type { Order, OrderStatus } from './orders'
export interface MeasurementRequest { actualWeight: number; actualLength: number; actualWidth: number; actualHeight: number; measuredAt: string; version: number }
export interface MeasurementResult { measurementId: Id; actualChargeableWeight: number; previousFee?: number; currentFee?: number; feeChangeType: 'UNCHANGED' | 'DECREASE' | 'INCREASE'; nextStatus: OrderStatus }
export interface OutboundResult { outboundRecordId?: Id; orderId?: Id; trackingNo?: string; currentStatus?: 'OUTBOUND' }
export const confirmInbound = async (orderId: Id, version: number) => unwrap<Order>(await http.post(`/orders/${orderId}/inbound`, { version }, writeConfig('inbound')))
export const submitMeasurement = async (orderId: Id, body: MeasurementRequest) => unwrap<MeasurementResult>(await http.post(`/orders/${orderId}/measurements`, body, writeConfig('measurement')))
export const confirmPrice = async (orderId: Id, body: { feeAdjustmentId: Id; expectedFee: number; version: number }) => unwrap<Order>(await http.post(`/orders/${orderId}/price-confirmation`, body, writeConfig('price-confirmation')))
export const confirmOutbound = async (orderId: Id, body: { trackingNo: string; outboundAt: string; handoverRemark?: string; version: number }) => unwrap<OutboundResult>(await http.post(`/orders/${orderId}/outbound`, body, writeConfig('outbound')))
