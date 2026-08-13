import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { ApiPage, Id, PageQuery } from '@/types/api'

export type ActiveStatus = 'ACTIVE' | 'DISABLED'
export type TenantStatus = 'PENDING' | ActiveStatus
export interface Tenant { id: Id; tenantCode: string; tenantName: string; status: TenantStatus; version: number; createdAt: string; updatedAt: string }
export interface CreateTenantRequest { tenantCode: string; tenantName: string; initialAdmin: { username: string; displayName: string; temporaryPassword: string } }
export interface TenantQuery extends PageQuery { status?: TenantStatus; tenantCode?: string }
export const listTenants = async (params: TenantQuery = {}) => unwrap<ApiPage<Tenant>>(await http.get('/platform/tenants', { params }))
export const getTenant = async (id: Id) => unwrap<Tenant>(await http.get(`/platform/tenants/${id}`))
export const createTenant = async (body: CreateTenantRequest) => unwrap<Tenant>(await http.post('/platform/tenants', body, writeConfig('tenant')))
export const updateTenant = async (id: Id, body: { tenantName: string; version: number }) => unwrap<Tenant>(await http.put(`/platform/tenants/${id}`, body, writeConfig('tenant-update')))
export const changeTenantStatus = async (id: Id, body: { status: ActiveStatus; version: number }) => unwrap<Tenant>(await http.post(`/platform/tenants/${id}/status`, body, writeConfig('tenant-status', false)))
