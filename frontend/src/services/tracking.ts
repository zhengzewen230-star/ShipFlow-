import { http, unwrap } from './http'
import type { ApiPage, Id, PageQuery } from '@/types/api'
import type { OrderStatus } from './orders'
export interface TrackingEvent { id: Id; trackingNo: string; eventId: string; eventCode: string; eventDescription?: string; eventTime: string; receivedTime: string; processStatus: 'PENDING' | 'PROCESSED' | 'RETRY' | 'REJECTED' }
export const listTrackingEvents = async (orderId: Id, params: PageQuery = {}) => unwrap<ApiPage<TrackingEvent>>(await http.get(`/orders/${orderId}/tracking-events`, { params }))
export interface ShipmentTrackingEvent { id: Id; trackingNo: string; eventCode: string; description?: string | null; eventTime: string; processStatus: 'PENDING' | 'PROCESSED' | 'RETRY' | 'REJECTED' }
export interface ShipmentTrackingStatus { orderId: Id; currentStatus: OrderStatus; latestEvent?: ShipmentTrackingEvent | null }
export const listShipmentOrderTracking = async (orderId: Id) => unwrap<ShipmentTrackingEvent[]>(await http.get(`/shipment-orders/${orderId}/tracking`))
export const getShipmentOrderTrackingStatus = async (orderId: Id) => unwrap<ShipmentTrackingStatus>(await http.get(`/shipment-orders/${orderId}/tracking/status`))

export interface ShipmentTrackingTimelineEvent {
  id: Id
  orderId: Id
  orderNo: string
  waybillNo?: string | null
  statusCode: string
  title: string
  description?: string | null
  location?: string | null
  source: 'INTERNAL' | 'SF_EXPRESS'
  occurredAt: string
}

export const getShipmentTrackingTimeline = async (reference: string) =>
  unwrap<ShipmentTrackingTimelineEvent[]>(await http.get(`/orders/${encodeURIComponent(reference)}/tracking`))

export interface TrackingEventListItem {
  id: Id
  orderId: Id
  orderNo: string
  trackingNo?: string | null
  occurredAt: string
  location?: string | null
  eventCode: string
  eventDescription?: string | null
  source: 'SF_EXPRESS' | 'INTERNAL' | 'OTHER'
  processStatus: 'PROCESSED' | 'RETRY' | 'REJECTED' | 'RETAINED'
  exception: boolean
  exceptionCode?: string | null
  exceptionId?: Id | null
  requestId?: string | null
}

export interface TrackingEventSearchQuery extends PageQuery {
  orderNo?: string
  trackingNo?: string
  status?: string
  from?: string
  to?: string
  sortDirection?: 'ASC' | 'DESC'
}

export const searchTrackingEvents = async (params: TrackingEventSearchQuery = {}) =>
  unwrap<ApiPage<TrackingEventListItem>>(await http.get('/tracking-events', { params }))
