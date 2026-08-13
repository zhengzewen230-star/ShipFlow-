import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { ApiPage, Id, PageQuery } from '@/types/api'
export type BillBatchStatus = 'PROCESSING' | 'PARTIAL_SUCCESS' | 'SUCCESS' | 'FAILED'
export interface BillBatch { id?: Id; batchNo?: string; fileHash?: string; status?: BillBatchStatus; totalCount?: number; successCount?: number; failureCount?: number }
export interface BillDetail { id?: Id; batchId?: Id; providerId?: Id; providerBillDetailNo?: string; lineNo?: number; shipmentOrderId?: Id | null; trackingNo?: string | null; billedAmount?: number; currency?: string; feeType?: string; detailStatus?: 'IMPORTED' | 'MATCHED' | 'ERROR'; errorMessage?: string | null }
export interface Reconciliation { id?: Id; shipmentOrderId?: Id; billDetailId?: Id; systemAmount?: number; billedAmount?: number; differenceAmount?: number; reconciliationStatus?: 'AUTO_CLOSED' | 'PENDING_CONFIRMATION' | 'CONFIRMED' | 'REJECTED'; version?: number }
export const importBillingCsv = async (file: File, providerId: Id) => { const body = new FormData(); body.append('file', file); body.append('providerId', providerId); return unwrap<BillBatch>(await http.post('/billing/import-batches', body, writeConfig('bill-import'))) }
export const listBillImportBatches = async (params: PageQuery & { providerId?: Id; status?: BillBatchStatus } = {}) => unwrap<ApiPage<BillBatch>>(await http.get('/billing/import-batches', { params }))
export const getBillImportBatch = async (id: Id) => unwrap<BillBatch>(await http.get(`/billing/import-batches/${id}`))
export const listBillImportErrors = async (id: Id, params: PageQuery = {}) => unwrap<ApiPage<BillDetail>>(await http.get(`/billing/import-batches/${id}/errors`, { params }))
export const listBillDetails = async (params: PageQuery & { batchId?: Id; status?: 'IMPORTED' | 'MATCHED' | 'ERROR' } = {}) => unwrap<ApiPage<BillDetail>>(await http.get('/billing/details', { params }))
export const listReconciliations = async (params: PageQuery & { orderId?: Id; status?: Reconciliation['reconciliationStatus'] } = {}) => unwrap<ApiPage<Reconciliation>>(await http.get('/reconciliations', { params }))
export const getReconciliation = async (id: Id) => unwrap<Reconciliation>(await http.get(`/reconciliations/${id}`))
export const confirmReconciliationDifference = async (id: Id, body: { resolutionType: string; remark: string; version: number }) => unwrap<Reconciliation>(await http.post(`/reconciliations/${id}/confirm`, body, writeConfig('reconciliation')))
