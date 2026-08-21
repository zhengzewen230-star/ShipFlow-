import type { LocationQuery } from 'vue-router'
import type { ActiveStatus } from '@/services/tenants'
import type { PublicLogisticsChannelSort } from '@/services/logistics'

export interface LogisticsFilterState {
  channelCode: string
  channelName: string
  serviceCountry: string
  status: ActiveStatus | ''
  page: number
  pageSize: number
  sortField: PublicLogisticsChannelSort
  sortDirection: 'ASC' | 'DESC'
}

export const defaultLogisticsFilter: LogisticsFilterState = { channelCode: '', channelName: '', serviceCountry: '', status: '', page: 1, pageSize: 20, sortField: 'updatedAt', sortDirection: 'DESC' }
const sortable: PublicLogisticsChannelSort[] = ['channelCode', 'channelName', 'providerName', 'status', 'effectiveFrom', 'updatedAt']
const value = (query: LocationQuery | Record<string, unknown>, key: string) => typeof query[key] === 'string' ? String(query[key]).trim() : ''
const positiveInt = (input: string, fallback: number, max: number) => { const parsed = Number(input); return Number.isInteger(parsed) && parsed >= 1 && parsed <= max ? parsed : fallback }

export function parseLogisticsQuery(query: LocationQuery | Record<string, unknown>): LogisticsFilterState {
  const status = value(query, 'status')
  const sortField = value(query, 'sortField') as PublicLogisticsChannelSort
  const sortDirection = value(query, 'sortDirection').toUpperCase()
  const country = value(query, 'serviceCountry').toUpperCase()
  return { channelCode: value(query, 'channelCode').slice(0, 64), channelName: value(query, 'channelName').slice(0, 128), serviceCountry: /^[A-Z]{2}$/.test(country) ? country : '', status: status === 'ACTIVE' || status === 'DISABLED' ? status : '', page: positiveInt(value(query, 'page'), 1, 1000000), pageSize: positiveInt(value(query, 'pageSize'), 20, 100), sortField: sortable.includes(sortField) ? sortField : defaultLogisticsFilter.sortField, sortDirection: sortDirection === 'ASC' || sortDirection === 'DESC' ? sortDirection : defaultLogisticsFilter.sortDirection }
}

export function toLogisticsQuery(state: LogisticsFilterState): Record<string, string> {
  const query: Record<string, string> = {}
  if (state.channelCode) query.channelCode = state.channelCode.trim()
  if (state.channelName) query.channelName = state.channelName.trim()
  if (state.serviceCountry) query.serviceCountry = state.serviceCountry
  if (state.status) query.status = state.status
  if (state.page > 1) query.page = String(state.page)
  if (state.pageSize !== defaultLogisticsFilter.pageSize) query.pageSize = String(state.pageSize)
  if (state.sortField !== defaultLogisticsFilter.sortField) query.sortField = state.sortField
  if (state.sortDirection !== defaultLogisticsFilter.sortDirection) query.sortDirection = state.sortDirection
  return query
}
