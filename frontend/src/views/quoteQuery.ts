import type { LocationQuery } from 'vue-router'
import type { Id } from '@/types/api'
import type { QuoteQuery } from '@/services/quotes'

export type QuoteSortField = NonNullable<QuoteQuery['sortField']>
export type QuoteSortDirection = NonNullable<QuoteQuery['sortDirection']>
export type QuoteStatus = NonNullable<QuoteQuery['status']>

export interface QuoteFilterState extends Required<Pick<QuoteQuery, 'page' | 'pageSize' | 'sortField' | 'sortDirection'>> {
  quoteNo: string
  storeId?: Id
  channelId?: Id
  destinationCountry: string
  status: QuoteStatus | ''
  createdFrom: string
  createdTo: string
  validFrom: string
  validTo: string
}

export const defaultQuoteFilter: QuoteFilterState = {
  quoteNo: '', destinationCountry: '', status: '', createdFrom: '', createdTo: '', validFrom: '', validTo: '',
  page: 1, pageSize: 20, sortField: 'createdAt', sortDirection: 'DESC',
}

const sortFields: QuoteSortField[] = ['createdAt', 'quoteNo', 'validTo', 'amount', 'status']
const statuses: QuoteStatus[] = ['VALID', 'EXPIRED', 'CANCELLED']

function value(query: LocationQuery | Record<string, unknown>, key: string) {
  return typeof query[key] === 'string' ? String(query[key]).trim() : ''
}

function positiveId(value: string): Id | undefined {
  return /^\d+$/.test(value) && Number(value) > 0 ? value : undefined
}

function positiveInt(value: string, fallback: number, max: number) {
  const parsed = Number(value)
  return Number.isInteger(parsed) && parsed >= 1 && parsed <= max ? parsed : fallback
}

function dateTime(value: string) {
  return value && !Number.isNaN(Date.parse(value)) ? value : ''
}

export function parseQuoteQuery(query: LocationQuery | Record<string, unknown>): QuoteFilterState {
  const country = value(query, 'destinationCountry').toUpperCase()
  const status = value(query, 'status') as QuoteStatus
  const sortField = value(query, 'sortField') as QuoteSortField
  const sortDirection = value(query, 'sortDirection').toUpperCase() as QuoteSortDirection
  return {
    quoteNo: value(query, 'quoteNo').slice(0, 64),
    storeId: positiveId(value(query, 'storeId')),
    channelId: positiveId(value(query, 'channelId')),
    destinationCountry: /^[A-Z]{2}$/.test(country) ? country : '',
    status: statuses.includes(status) ? status : '',
    createdFrom: dateTime(value(query, 'createdFrom')),
    createdTo: dateTime(value(query, 'createdTo')),
    validFrom: dateTime(value(query, 'validFrom')),
    validTo: dateTime(value(query, 'validTo')),
    page: positiveInt(value(query, 'page'), defaultQuoteFilter.page, 1_000_000),
    pageSize: positiveInt(value(query, 'pageSize'), defaultQuoteFilter.pageSize, 100),
    sortField: sortFields.includes(sortField) ? sortField : defaultQuoteFilter.sortField,
    sortDirection: sortDirection === 'ASC' || sortDirection === 'DESC' ? sortDirection : defaultQuoteFilter.sortDirection,
  }
}

export function toQuoteApiQuery(state: QuoteFilterState): QuoteQuery {
  return {
    quoteNo: state.quoteNo.trim() || undefined,
    storeId: state.storeId,
    channelId: state.channelId,
    destinationCountry: state.destinationCountry || undefined,
    status: state.status || undefined,
    createdFrom: state.createdFrom || undefined,
    createdTo: state.createdTo || undefined,
    validFrom: state.validFrom || undefined,
    validTo: state.validTo || undefined,
    page: state.page,
    pageSize: state.pageSize,
    sortField: state.sortField,
    sortDirection: state.sortDirection,
  }
}

export function toQuoteRouteQuery(state: QuoteFilterState): Record<string, string> {
  const query: Record<string, string> = {}
  if (state.quoteNo.trim()) query.quoteNo = state.quoteNo.trim()
  if (state.storeId) query.storeId = String(state.storeId)
  if (state.channelId) query.channelId = String(state.channelId)
  if (state.destinationCountry) query.destinationCountry = state.destinationCountry
  if (state.status) query.status = state.status
  if (state.createdFrom) query.createdFrom = state.createdFrom
  if (state.createdTo) query.createdTo = state.createdTo
  if (state.validFrom) query.validFrom = state.validFrom
  if (state.validTo) query.validTo = state.validTo
  if (state.page > defaultQuoteFilter.page) query.page = String(state.page)
  if (state.pageSize !== defaultQuoteFilter.pageSize) query.pageSize = String(state.pageSize)
  if (state.sortField !== defaultQuoteFilter.sortField) query.sortField = state.sortField
  if (state.sortDirection !== defaultQuoteFilter.sortDirection) query.sortDirection = state.sortDirection
  return query
}
