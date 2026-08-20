import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { ApiPage, Id, PageQuery } from '@/types/api'
export type ExceptionStatus = 'OPEN' | 'PROCESSING' | 'WAITING_PROVIDER_FEEDBACK' | 'RESOLVED' | 'PENDING_FINANCE_CONFIRMATION' | 'CLOSED'
export type ClaimStatus = 'OPEN' | 'SUBMITTED' | 'APPROVED' | 'PARTIALLY_APPROVED' | 'REJECTED' | 'CLOSED'
export type ResponsibleParty = 'MERCHANT' | 'PROVIDER' | 'CUSTOMS' | 'CUSTOMER' | 'OTHER'
export interface Claim { id: Id; exceptionId: Id; claimNo: string; status: ClaimStatus; claimAmount: number; currency: string; submittedAt?: string | null; resolvedAt?: string | null; version: number; createdAt: string; updatedAt: string }
export interface HandlingRecord { id: Id; exceptionId: Id; recordNo: string; handledByUserId: Id; recordType: 'CONTACT' | 'FOLLOW_UP' | 'PROVIDER_FEEDBACK' | 'INTERNAL_NOTE' | 'OTHER'; content: string; createdAt: string }
export interface EvidenceAttachment { id: Id; exceptionId: Id; uploadedByUserId: Id; originalFileName: string; contentType: string; fileSize: number; contentSha256: string; description?: string | null; createdAt: string }
export interface ExceptionCase { id: Id; orderId: Id; storeId: Id; exceptionNo: string; exceptionType: 'ADDRESS' | 'CUSTOMS' | 'TRANSPORT' | 'OTHER'; status: ExceptionStatus; description: string; reportedAt: string; assignedToUserId?: Id | null; responsibleParty?: ResponsibleParty | null; version: number; createdAt: string; updatedAt: string; claim?: Claim | null }
export interface ExceptionQuery extends PageQuery { orderId?: Id; orderNo?: string; storeId?: Id; exceptionType?: ExceptionCase['exceptionType']; status?: ExceptionStatus; responsibleParty?: ResponsibleParty; createdFrom?: string; createdTo?: string; sortBy?: 'createdAt' | 'updatedAt' | 'status' | 'exceptionType'; sortDirection?: 'ASC' | 'DESC'; workbenchFilter?: 'PENDING_FOLLOW_UP' }
export const listExceptionCases = async (params: ExceptionQuery = {}) => unwrap<ApiPage<ExceptionCase>>(await http.get('/exceptions', { params }))
export const getExceptionCase = async (id: Id) => unwrap<ExceptionCase>(await http.get(`/exceptions/${id}`))
export const createExceptionCase = async (orderId: Id, body: { exceptionType: ExceptionCase['exceptionType']; description: string; reportedAt: string; trackingEventId?: Id | null }) => unwrap<ExceptionCase>(await http.post(`/orders/${orderId}/exceptions`, body, writeConfig('exception')))
export const assignExceptionCase = async (id: Id, body: { assignedToUserId: Id; responsibleParty: ResponsibleParty; reason?: string; version: number }) => unwrap<ExceptionCase>(await http.post(`/exceptions/${id}/assign`, body, writeConfig('exception-assign')))
export const transitionExceptionCase = async (id: Id, body: { status: Exclude<ExceptionStatus, 'OPEN'>; reason: string; version: number }) => unwrap<ExceptionCase>(await http.post(`/exceptions/${id}/status`, body, writeConfig('exception-status')))
export const listHandlingRecords = async (id: Id) => unwrap<HandlingRecord[]>(await http.get(`/exceptions/${id}/handling-records`))
export const addHandlingRecord = async (id: Id, body: { recordType: HandlingRecord['recordType']; content: string }) => unwrap<HandlingRecord>(await http.post(`/exceptions/${id}/handling-records`, body, writeConfig('exception-handling')))
export const listEvidence = async (id: Id) => unwrap<EvidenceAttachment[]>(await http.get(`/exceptions/${id}/evidence`))
export const uploadEvidence = async (id: Id, file: File, description?: string) => { const form = new FormData(); form.append('file', file); if (description?.trim()) form.append('description', description.trim()); const config = writeConfig('exception-evidence'); return unwrap<EvidenceAttachment>(await http.post(`/exceptions/${id}/evidence`, form, { ...config, headers: { ...config.headers, 'Content-Type': 'multipart/form-data' } })) }
export const downloadEvidence = async (exceptionId: Id, attachmentId: Id) => http.get(`/exceptions/${exceptionId}/evidence/${attachmentId}/content`, { responseType: 'blob' })
export const createClaim = async (id: Id, body: { claimAmount: number; currency: string }) => unwrap<Claim>(await http.post(`/exceptions/${id}/claim`, body, writeConfig('claim')))
export const getClaim = async (id: Id) => unwrap<Claim>(await http.get(`/claims/${id}`))
export const submitClaim = async (id: Id, body: { reason?: string; version: number }) => unwrap<Claim>(await http.post(`/claims/${id}/submit`, body, writeConfig('claim-submit', false)))
export const resolveClaim = async (id: Id, body: { status: 'APPROVED' | 'REJECTED'; reason: string; resolvedAt: string; version: number }) => unwrap<Claim>(await http.post(`/claims/${id}/result`, body, writeConfig('claim-result', false)))
export const closeClaim = async (id: Id, body: { reason?: string; version: number }) => unwrap<Claim>(await http.post(`/claims/${id}/close`, body, writeConfig('claim-close', false)))
