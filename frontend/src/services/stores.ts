import { http, unwrap } from './http'
import { writeConfig } from './request'
import type { ApiPage, Id } from '@/types/api'
import type { ActiveStatus } from './tenants'
export interface Store { id: Id; tenantId: Id; storeCode: string; storeName: string; platformCode: string; platformAccount: string; status: ActiveStatus; version: number; createdAt: string; updatedAt: string }
export interface StoreInput { storeCode: string; storeName: string; platformCode: string; platformAccount: string }
export const listStores = async (params: { status?: ActiveStatus; platformCode?: string } = {}) => unwrap<ApiPage<Store>>(await http.get('/stores', { params }))
export const getStore = async (id: Id) => unwrap<Store>(await http.get(`/stores/${id}`))
export const createStore = async (body: StoreInput) => unwrap<Store>(await http.post('/stores', body, writeConfig('store')))
export const updateStore = async (id: Id, body: Omit<StoreInput, 'storeCode'> & { version: number }) => unwrap<Store>(await http.put(`/stores/${id}`, body, writeConfig('store-update')))
export const changeStoreStatus = async (id: Id, body: { status: ActiveStatus; version: number }) => unwrap<Store>(await http.post(`/stores/${id}/status`, body, writeConfig('store-status', false)))
