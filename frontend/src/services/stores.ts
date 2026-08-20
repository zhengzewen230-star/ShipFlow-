import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { ApiPage, Id, PageQuery } from '@/types/api'
import type { ActiveStatus } from './tenants'
export type { ActiveStatus } from './tenants'
export interface Store { id: Id; tenantId: Id; storeCode: string; storeName: string; platformCode: string; countryRegion: string | null; defaultShippingAddress: string | null; defaultLogisticsChannel: string | null; status: ActiveStatus; version: number; updatedAt: string }
export interface StoreAuditSummary { actionType: string; resultStatus: string; occurredAt: string | null }
export interface StoreDetail { id: Id; tenantId: Id; storeCode: string; storeName: string; platformCode: string; platformAccountMasked: string; countryRegion: string | null; defaultShippingAddress: string | null; defaultLogisticsChannel: string | null; status: ActiveStatus; version: number; createdAt: string | null; updatedAt: string | null; historicalOrderCount: number; auditSummary: StoreAuditSummary[]; unavailableFields: string[]; configurationVersion: number }
export interface StoreAddress { id: Id; tenantId: Id; storeId: Id; addressCode: string; contactName: string; companyName: string | null; phone: string; email: string | null; countryCode: string; stateProvince: string | null; city: string; district: string | null; addressLine1: string; addressLine2: string | null; postalCode: string; status: ActiveStatus; isDefault: boolean; version: number; createdAt: string; updatedAt: string }
export interface StoreChannel { id: Id; tenantId: Id; storeId: Id; channelId: Id; channelCode: string; channelName: string; providerName: string; status: ActiveStatus; isDefault: boolean; version: number; createdBy: Id | null; updatedBy: Id | null; createdAt: string; updatedAt: string }
export interface StoreInput { storeCode: string; storeName: string; platformCode: string; platformAccount: string }
export interface StoreQuery extends PageQuery { storeCode?: string; storeName?: string; platformCode?: string; status?: ActiveStatus; sortBy?: 'storeCode' | 'storeName' | 'platformCode' | 'status' | 'updatedAt'; sortDirection?: 'ASC' | 'DESC' }
export const listStores = async (params: StoreQuery = {}) => unwrap<ApiPage<Store>>(await http.get('/stores', { params }))
export const getStore = async (id: Id) => unwrap<StoreDetail>(await http.get(`/stores/${id}`))
export const getDefaultStoreAddress = async (id: Id) => unwrap<StoreAddress | null>(await http.get(`/stores/${id}/default-address`))
export const updateDefaultStoreAddress = async (id: Id, body: Omit<StoreAddress, 'id' | 'tenantId' | 'storeId' | 'status' | 'isDefault' | 'createdAt' | 'updatedAt'>) => unwrap<StoreAddress>(await http.put(`/stores/${id}/default-address`, body, writeConfig('store-address')))
export const listStoreLogisticsChannels = async (id: Id) => unwrap<StoreChannel[]>(await http.get(`/stores/${id}/logistics-channels`))
export const setDefaultStoreLogisticsChannel = async (id: Id, body: { channelId: Id; version: number }) => unwrap<StoreChannel[]>(await http.put(`/stores/${id}/default-logistics-channel`, body, writeConfig('store-channel')))
export const createStore = async (body: StoreInput) => unwrap<Store>(await http.post('/stores', body, writeConfig('store')))
export const updateStore = async (id: Id, body: Omit<StoreInput, 'storeCode'> & { version: number }) => unwrap<Store>(await http.put(`/stores/${id}`, body, writeConfig('store-update')))
export const changeStoreStatus = async (id: Id, body: { status: ActiveStatus; version: number }) => unwrap<Store>(await http.post(`/stores/${id}/status`, body, writeConfig('store-status')))
