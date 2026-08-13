import { http, unwrap } from './http'
import type { ApiPage, Id, PageQuery } from '@/types/api'
import type { OrderStatus } from './orders'
export interface TrackingEvent { id: Id; trackingNo: string; eventId: string; eventCode: string; eventDescription?: string; eventTime: string; receivedTime: string; processStatus: 'PENDING' | 'PROCESSED' | 'RETRY' | 'REJECTED' }
export const listTrackingEvents = async (orderId: Id, params: PageQuery = {}) => unwrap<ApiPage<TrackingEvent>>(await http.get(`/orders/${orderId}/tracking-events`, { params }))
export interface ShipmentTrackingEvent { id: Id; trackingNo: string; eventCode: string; description?: string | null; eventTime: string; processStatus: 'PENDING' | 'PROCESSED' | 'RETRY' | 'REJECTED' }
export interface ShipmentTrackingStatus { orderId: Id; currentStatus: OrderStatus; latestEvent?: ShipmentTrackingEvent | null }
export const listShipmentOrderTracking = async (orderId: Id) => unwrap<ShipmentTrackingEvent[]>(await http.get(`/shipment-orders/${orderId}/tracking`))
export const getShipmentOrderTrackingStatus = async (orderId: Id) => unwrap<ShipmentTrackingStatus>(await http.get(`/shipment-orders/${orderId}/tracking/status`))
