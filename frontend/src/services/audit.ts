import { http, unwrap } from './http'
import type { ApiPage, Id, JsonObject, PageQuery } from '@/types/api'
export interface AuditLog { id: Id; tenantId: Id; operatorUserId?: Id | null; actionType: string; resourceType: string; resourceId?: Id | null; resultStatus: 'SUCCESS' | 'FAILURE' | 'REJECTED'; detail: JsonObject; occurredAt: string; createdAt: string }
export interface AuditQuery extends PageQuery { resourceType?: string; resourceId?: Id; action?: string; operatorId?: Id; from?: string; to?: string }
export const listTenantAuditLogs = async (params: AuditQuery = {}) => unwrap<ApiPage<AuditLog>>(await http.get('/audit-logs', { params }))
export const getTenantAuditLog = async (id: Id) => unwrap<AuditLog>(await http.get(`/audit-logs/${id}`))
export const listPlatformTenantAuditLogs = async (tenantId: Id, params: AuditQuery = {}) => unwrap<ApiPage<AuditLog>>(await http.get('/platform/audit-logs', { params: { tenantId, ...params } }))
export const getPlatformTenantAuditLog = async (tenantId: Id, id: Id) => unwrap<AuditLog>(await http.get(`/platform/audit-logs/${id}`, { params: { tenantId } }))
