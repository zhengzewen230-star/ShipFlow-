import type { LocationQuery } from 'vue-router'
import type { PageQuery } from '@/types/api'
import type { ActiveStatus } from '@/services/tenants'

export const storeSortFields = ['storeCode', 'storeName', 'platformCode', 'status', 'updatedAt'] as const
export type StoreSortField = typeof storeSortFields[number]
export type StoreSortDirection = 'ASC' | 'DESC'

export interface StoreFilterState extends PageQuery {
  storeCode: string
  storeName: string
  platformCode: string
  status: ActiveStatus | ''
  page: number
  pageSize: number
  sortBy: StoreSortField
  sortDirection: StoreSortDirection
}

export const defaultStoreFilter: StoreFilterState = {
  storeCode: '', storeName: '', platformCode: '', status: '', page: 1, pageSize: 20, sortBy: 'updatedAt', sortDirection: 'DESC',
}

function queryValue(query: LocationQuery | Record<string, unknown>, key: string) {
  const value = query[key]
  return typeof value === 'string' ? value.trim() : ''
}

function positiveInt(value: string, fallback: number, max: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 1 && parsed <= max ? parsed : fallback
}

export function parseStoreQuery(query: LocationQuery | Record<string, unknown>): StoreFilterState {
  const status = queryValue(query, 'status')
  const sortBy = queryValue(query, 'sortBy')
  const sortDirection = queryValue(query, 'sortDirection').toUpperCase()
  return {
    storeCode: queryValue(query, 'storeCode').slice(0, 64),
    storeName: queryValue(query, 'storeName').slice(0, 128),
    platformCode: queryValue(query, 'platformCode').slice(0, 64),
    status: status === 'ACTIVE' || status === 'DISABLED' ? status : '',
    page: positiveInt(queryValue(query, 'page'), 1, 1000000),
    pageSize: positiveInt(queryValue(query, 'pageSize'), 20, 100),
    sortBy: (storeSortFields.includes(sortBy as StoreSortField) ? sortBy : defaultStoreFilter.sortBy) as StoreSortField,
    sortDirection: sortDirection === 'ASC' || sortDirection === 'DESC' ? sortDirection : defaultStoreFilter.sortDirection,
  }
}

export function toStoreQuery(state: StoreFilterState): Record<string, string> {
  const query: Record<string, string> = {}
  if (state.storeCode) query.storeCode = state.storeCode.trim()
  if (state.storeName) query.storeName = state.storeName.trim()
  if (state.platformCode) query.platformCode = state.platformCode.trim()
  if (state.status) query.status = state.status
  if (state.page > 1) query.page = String(state.page)
  if (state.pageSize !== defaultStoreFilter.pageSize) query.pageSize = String(state.pageSize)
  if (state.sortBy !== defaultStoreFilter.sortBy) query.sortBy = state.sortBy
  if (state.sortDirection !== defaultStoreFilter.sortDirection) query.sortDirection = state.sortDirection
  return query
}
