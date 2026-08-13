import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { ApiPage, Id, PageQuery } from '@/types/api'
export type ExceptionStatus = 'OPEN' | 'PROCESSING' | 'RESOLVED' | 'CLOSED'
export type ClaimStatus = 'OPEN' | 'SUBMITTED' | 'APPROVED' | 'REJECTED' | 'CLOSED'
export interface Claim { id: Id; exceptionId: Id; claimNo: string; status: ClaimStatus; claimAmount: number; currency: string; submittedAt?: string | null; resolvedAt?: string | null; version: number; createdAt: string; updatedAt: string }
export interface ExceptionCase { id: Id; orderId: Id; exceptionNo: string; exceptionType: 'ADDRESS' | 'CUSTOMS' | 'TRANSPORT' | 'OTHER'; status: ExceptionStatus; description: string; reportedAt: string; assignedToUserId?: Id | null; version: number; createdAt: string; updatedAt: string; claim?: Claim | null }
export interface ExceptionQuery extends PageQuery { orderId?: Id; status?: ExceptionStatus }
export const listExceptionCases = async (params: ExceptionQuery = {}) => unwrap<ApiPage<ExceptionCase>>(await http.get('/exceptions', { params }))
export const getExceptionCase = async (id: Id) => unwrap<ExceptionCase>(await http.get(`/exceptions/${id}`))
export const createExceptionCase = async (orderId: Id, body: { exceptionType: ExceptionCase['exceptionType']; description: string; reportedAt: string; trackingEventId?: Id | null }) => unwrap<ExceptionCase>(await http.post(`/orders/${orderId}/exceptions`, body, writeConfig('exception')))
export const assignExceptionCase = async (id: Id, body: { assignedToUserId: Id; reason?: string; version: number }) => unwrap<ExceptionCase>(await http.post(`/exceptions/${id}/assign`, body, writeConfig('exception-assign', false)))
export const transitionExceptionCase = async (id: Id, body: { status: 'RESOLVED' | 'CLOSED'; reason: string; version: number }) => unwrap<ExceptionCase>(await http.post(`/exceptions/${id}/status`, body, writeConfig('exception-status', false)))
export const createClaim = async (id: Id, body: { claimAmount: number; currency: string }) => unwrap<Claim>(await http.post(`/exceptions/${id}/claim`, body, writeConfig('claim')))
export const getClaim = async (id: Id) => unwrap<Claim>(await http.get(`/claims/${id}`))
export const submitClaim = async (id: Id, body: { reason?: string; version: number }) => unwrap<Claim>(await http.post(`/claims/${id}/submit`, body, writeConfig('claim-submit', false)))
export const resolveClaim = async (id: Id, body: { status: 'APPROVED' | 'REJECTED'; reason: string; resolvedAt: string; version: number }) => unwrap<Claim>(await http.post(`/claims/${id}/result`, body, writeConfig('claim-result', false)))
export const closeClaim = async (id: Id, body: { reason?: string; version: number }) => unwrap<Claim>(await http.post(`/claims/${id}/close`, body, writeConfig('claim-close', false)))
