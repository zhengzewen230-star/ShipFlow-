import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { ApiPage, Id, PageQuery } from '@/types/api'

export interface Address { contactName: string; companyName?: string; phone: string; email?: string; countryCode: string; stateProvince?: string; city: string; district?: string; addressLine1: string; addressLine2?: string; postalCode: string }
export interface Item { sku: string; productName: string; quantity: number; unitPrice: number; currency: string; hsCode?: string; countryOfOrigin?: string }
export type OrderStatus = 'DRAFT' | 'PENDING_INBOUND' | 'INBOUND' | 'PENDING_PRICE_CONFIRMATION' | 'READY_FOR_OUTBOUND' | 'OUTBOUND' | 'IN_TRANSIT' | 'DELIVERED' | 'CANCELLED' | 'RETURNED' | 'LOST'
export interface Order { id: Id; orderNo: string; quoteId: Id; currentStatus: OrderStatus; estimatedFee: number; currentFee: number; confirmedFee?: number | null; currency: string; version: number }
export interface ShipmentOrder { id: Id; orderNo: string; storeId?: Id; quoteId: Id; status: OrderStatus; destinationCountry?: string; channelId?: Id; trackingNo?: string | null; estimatedFee: number; currentFee?: number | null; confirmedFee?: number | null; currency: string; chargeableWeight: number; version: number; createdAt: string; addresses?: unknown[]; packages?: unknown[]; feeAdjustments?: unknown[]; warehouseRecords?: unknown[]; tracking?: unknown[]; exceptions?: unknown[]; timeline?: unknown[] }
export interface PriceConfirmationRequest { feeAdjustmentId: Id; expectedFee: number; version: number }
export interface PriceConfirmationView { id: Id; orderNo: string; quoteId: Id; storeId: Id; currentStatus: OrderStatus; estimatedFee: number; currentFee: number; confirmedFee?: number | null; currency: string; chargeableWeight?: number; version: number; feeAdjustmentId: Id; adjustmentType: 'INCREASE'; beforeAmount: number; afterAmount: number; differenceAmount: number; confirmationStatus: 'PENDING_CONFIRMATION' | 'REQUESTED' | 'CONFIRMED'; requestedAt?: string | null; confirmedAt?: string | null }
export interface ShipmentOrderQuery extends PageQuery { orderNo?: string; status?: OrderStatus; storeId?: Id; destinationCountry?: string; channelId?: Id; trackingNo?: string; createdFrom?: string; createdTo?: string; sortBy?: 'createdAt' | 'orderNo' | 'status' | 'destinationCountry' | 'channelId' | 'estimatedFee'; sortDirection?: 'ASC' | 'DESC'; workbenchFilter?: 'PENDING_FEE_CONFIRMATION' | 'MISSING_ADDRESS' }
export interface UpdateShipmentOrderRequest { version: number; senderAddress: Address; receiverAddress: Address; items: Item[]; remark?: string }
const versions = new Map<string, number>()
const remember = (order: ShipmentOrder) => { versions.set(String(order.id), order.version); return order }
const latestVersion = (id: Id, fallback: number) => versions.get(String(id)) ?? fallback
const rememberPriceConfirmation = (view: PriceConfirmationView) => { versions.set(String(view.id), view.version); return view }

export const listShipmentOrders = async (params: ShipmentOrderQuery = {}) => {
  const page = unwrap<ApiPage<ShipmentOrder>>(await http.get('/orders', { params }))
  page.items.forEach(remember)
  return page
}
export const getOrder = async (id: Id) => remember(unwrap<ShipmentOrder>(await http.get(`/orders/${id}`)))
export const getPriceConfirmation = async (id: Id) => rememberPriceConfirmation(unwrap<PriceConfirmationView>(await http.get(`/orders/${id}/price-confirmation`)))
export const requestPriceConfirmation = async (id: Id, body: PriceConfirmationRequest) => rememberPriceConfirmation(unwrap<PriceConfirmationView>(await http.post(`/orders/${id}/price-confirmation-requests`, { ...body, version: latestVersion(id, body.version) }, writeConfig('price-confirmation-request'))))
export const confirmPrice = async (id: Id, body: PriceConfirmationRequest) => rememberPriceConfirmation(unwrap<PriceConfirmationView>(await http.post(`/orders/${id}/price-confirmation`, { ...body, version: latestVersion(id, body.version) }, writeConfig('price-confirmation'))))
export const updateDraftShipmentOrder = async (id: Id, body: UpdateShipmentOrderRequest) => remember(unwrap<ShipmentOrder>(await http.put(`/orders/${id}`, { ...body, version: latestVersion(id, body.version) }, writeConfig('order-draft', false))))
export const submitOrder = async (id: Id, version: number, reason?: string) => remember(unwrap<ShipmentOrder>(await http.post(`/orders/${id}/submit`, { version: latestVersion(id, version), reason }, writeConfig('order-submit'))))
export const cancelOrder = async (id: Id, body: { reason?: string; version: number }) => remember(unwrap<ShipmentOrder>(await http.post(`/orders/${id}/cancel`, { ...body, version: latestVersion(id, body.version) }, writeConfig('order-cancel'))))
export const createShipmentOrderFromQuote = async (quoteId: Id, body: { senderAddress: Address; receiverAddress: Address; items: Item[] }) => remember(unwrap<ShipmentOrder>(await http.post(`/quotes/${quoteId}/shipment-orders`, body, writeConfig('shipment-order'))))
export const exportShipmentOrders = async (params: ShipmentOrderQuery & { ids?: Id[] } = {}) => http.get('/orders/export', { params, responseType: 'blob' })
