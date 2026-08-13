import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { ApiPage, Id, PageQuery } from '@/types/api'

export interface Address { contactName: string; companyName?: string; phone: string; email?: string; countryCode: string; stateProvince?: string; city: string; district?: string; addressLine1: string; addressLine2?: string; postalCode: string }
export interface Item { sku: string; productName: string; quantity: number; unitPrice: number; currency: string; hsCode?: string; countryOfOrigin?: string }
export type OrderStatus = 'DRAFT' | 'PENDING_INBOUND' | 'INBOUND' | 'PENDING_PRICE_CONFIRMATION' | 'READY_FOR_OUTBOUND' | 'OUTBOUND' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED' | 'RETURNED' | 'LOST'
export interface Order { id: Id; orderNo: string; quoteId: Id; currentStatus: OrderStatus; estimatedFee: number; currentFee: number; confirmedFee?: number | null; currency: string; version: number }
export interface ShipmentOrder { id: Id; orderNo: string; quoteId: Id; status: OrderStatus; estimatedFee: number; currency: string; chargeableWeight: number; version: number; createdAt: string }
export interface ShipmentOrderQuery extends PageQuery { status?: OrderStatus; storeId?: Id }
export interface UpdateShipmentOrderRequest { version: number; senderAddress: Address; receiverAddress: Address; items: Item[]; remark?: string }
const versions = new Map<string, number>()
const remember = (order: ShipmentOrder) => { versions.set(String(order.id), order.version); return order }
const latestVersion = (id: Id, fallback: number) => versions.get(String(id)) ?? fallback

export const listShipmentOrders = async (params: ShipmentOrderQuery = {}) => {
  const page = unwrap<ApiPage<ShipmentOrder>>(await http.get('/orders', { params }))
  page.items.forEach(remember)
  return page
}
export const getOrder = async (id: Id) => remember(unwrap<ShipmentOrder>(await http.get(`/orders/${id}`)))
export const updateDraftShipmentOrder = async (id: Id, body: UpdateShipmentOrderRequest) => remember(unwrap<ShipmentOrder>(await http.put(`/orders/${id}`, { ...body, version: latestVersion(id, body.version) }, writeConfig('order-draft', false))))
export const submitOrder = async (id: Id, version: number, reason?: string) => remember(unwrap<ShipmentOrder>(await http.post(`/orders/${id}/submit`, { version: latestVersion(id, version), reason }, writeConfig('order-submit', false))))
export const cancelOrder = async (id: Id, body: { reason?: string; version: number }) => remember(unwrap<ShipmentOrder>(await http.post(`/orders/${id}/cancel`, { ...body, version: latestVersion(id, body.version) }, writeConfig('order-cancel', false))))
export const createShipmentOrderFromQuote = async (quoteId: Id, body: { senderAddress: Address; receiverAddress: Address; items: Item[] }) => remember(unwrap<ShipmentOrder>(await http.post(`/quotes/${quoteId}/shipment-orders`, body, writeConfig('shipment-order'))))
