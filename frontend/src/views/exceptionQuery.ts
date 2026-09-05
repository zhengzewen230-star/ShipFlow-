import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import type { ExceptionCase, ExceptionQuery, ExceptionStatus, ResponsibleParty } from '@/services/exceptions'

export interface ExceptionFilterState extends Omit<ExceptionQuery, 'page' | 'pageSize'> {
  orderId: string
  orderNo: string
  storeId: string
  exceptionType?: ExceptionCase['exceptionType']
  status?: ExceptionStatus
  responsibleParty?: ResponsibleParty
  createdFrom: string
  createdTo: string
  page: number
  pageSize: number
  sortBy: NonNullable<ExceptionQuery['sortBy']>
  sortDirection: NonNullable<ExceptionQuery['sortDirection']>
}

export const defaultExceptionFilter: ExceptionFilterState = {
  orderId: '', orderNo: '', storeId: '', exceptionType: undefined, status: undefined,
  responsibleParty: undefined, createdFrom: '', createdTo: '', page: 1, pageSize: 20,
  sortBy: 'createdAt', sortDirection: 'DESC',
}

const one = (input: unknown) => Array.isArray(input) ? String(input[0] ?? '') : typeof input === 'string' ? input.trim() : ''
const positiveInt = (input: unknown, fallback: number, max: number) => {
  const parsed = Number(one(input))
  return Number.isInteger(parsed) && parsed >= 1 && parsed <= max ? parsed : fallback
}
const id = (input: unknown) => /^\d+$/.test(one(input)) && Number(one(input)) > 0 ? one(input) : ''
const dateTime = (input: unknown) => one(input) && !Number.isNaN(Date.parse(one(input))) ? one(input) : ''

export function parseExceptionQuery(query: LocationQuery | Record<string, unknown>): ExceptionFilterState {
  const exceptionType = one(query.exceptionType) as ExceptionCase['exceptionType']
  const status = one(query.status) as ExceptionStatus
  const responsibleParty = one(query.responsibleParty) as ResponsibleParty
  const sortBy = one(query.sortBy) as NonNullable<ExceptionQuery['sortBy']>
  const sortDirection = one(query.sortDirection).toUpperCase() as NonNullable<ExceptionQuery['sortDirection']>
  return {
    orderId: id(query.orderId),
    orderNo: one(query.orderNo).slice(0, 64),
    storeId: id(query.storeId),
    exceptionType: ['ADDRESS', 'CUSTOMS', 'TRANSPORT', 'OTHER'].includes(exceptionType) ? exceptionType : undefined,
    status: ['OPEN', 'PROCESSING', 'WAITING_PROVIDER_FEEDBACK', 'PENDING_FINANCE_CONFIRMATION', 'RESOLVED', 'CLOSED'].includes(status) ? status : undefined,
    responsibleParty: ['MERCHANT', 'PROVIDER', 'CUSTOMS', 'CUSTOMER', 'OTHER'].includes(responsibleParty) ? responsibleParty : undefined,
    createdFrom: dateTime(query.createdFrom),
    createdTo: dateTime(query.createdTo),
    page: positiveInt(query.page, 1, 1_000_000),
    pageSize: [20, 50, 100].includes(positiveInt(query.pageSize, 20, 100)) ? positiveInt(query.pageSize, 20, 100) : 20,
    sortBy: ['createdAt', 'updatedAt', 'status', 'exceptionType', 'orderNo'].includes(sortBy) ? sortBy : 'createdAt',
    sortDirection: sortDirection === 'ASC' ? 'ASC' : 'DESC',
  }
}

export function toExceptionRouteQuery(filter: ExceptionFilterState, exceptionId?: string): LocationQueryRaw {
  const query: LocationQueryRaw = {}
  const put = (key: string, value: unknown) => { if (value !== undefined && value !== null && value !== '') query[key] = String(value) }
  put('orderId', filter.orderId); put('orderNo', filter.orderNo); put('storeId', filter.storeId)
  put('exceptionType', filter.exceptionType); put('status', filter.status); put('responsibleParty', filter.responsibleParty)
  put('createdFrom', filter.createdFrom); put('createdTo', filter.createdTo); put('sortBy', filter.sortBy)
  put('sortDirection', filter.sortDirection); put('page', filter.page); put('pageSize', filter.pageSize); put('exceptionId', exceptionId)
  return query
}

export function toExceptionApiQuery(filter: ExceptionFilterState): ExceptionQuery {
  return {
    orderId: filter.orderId || undefined, orderNo: filter.orderNo || undefined, storeId: filter.storeId || undefined,
    exceptionType: filter.exceptionType, status: filter.status, responsibleParty: filter.responsibleParty,
    createdFrom: filter.createdFrom ? new Date(filter.createdFrom).toISOString() : undefined,
    createdTo: filter.createdTo ? new Date(filter.createdTo).toISOString() : undefined,
    page: filter.page, pageSize: filter.pageSize, sortBy: filter.sortBy, sortDirection: filter.sortDirection,
  }
}
