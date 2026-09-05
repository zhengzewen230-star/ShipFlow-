import type { LocationQuery, LocationQueryRaw } from 'vue-router'
import type { OrderStatus, ShipmentOrderQuery } from '@/services/orders'

export interface OrderFilterState extends ShipmentOrderQuery { orderNo: string; destinationCountry: string; channelId: string; trackingNo: string; createdFrom: string; createdTo: string; sortBy: 'createdAt' | 'orderNo' | 'status' | 'destinationCountry' | 'channelId' | 'estimatedFee'; sortDirection: 'ASC' | 'DESC'; page: number; pageSize: number }
export const defaultOrderFilter: OrderFilterState = { orderNo: '', storeId: undefined, status: undefined, workbenchFilter: undefined, destinationCountry: '', channelId: '', trackingNo: '', createdFrom: '', createdTo: '', sortBy: 'createdAt', sortDirection: 'DESC', page: 1, pageSize: 20 }
const one = (value: unknown) => Array.isArray(value) ? value[0] : typeof value === 'string' ? value : ''
const integer = (value: unknown) => { const result = Number(one(value)); return Number.isInteger(result) && result > 0 ? result : undefined }
const id = (value: unknown) => integer(value) == null ? undefined : String(integer(value))
export function parseOrderQuery(query: LocationQuery): OrderFilterState {
  const requestedStatus = one(query.status)
  const requestedWorkbenchFilter = one(query.workbenchFilter)
  const workbenchFilter = (requestedWorkbenchFilter || requestedStatus) === 'PENDING_FEE_CONFIRMATION'
    ? 'PENDING_FEE_CONFIRMATION'
    : (requestedWorkbenchFilter || requestedStatus) === 'MISSING_ADDRESS' ? 'MISSING_ADDRESS' : undefined
  const status = workbenchFilter ? undefined : requestedStatus as OrderStatus
  const sortBy = one(query.sortBy) as OrderFilterState['sortBy']
  const sortDirection = one(query.sortDirection) as OrderFilterState['sortDirection']
  return { ...defaultOrderFilter, orderNo: one(query.orderNo), storeId: id(query.storeId), status: status || undefined, workbenchFilter, destinationCountry: one(query.destinationCountry), channelId: one(query.channelId), trackingNo: one(query.trackingNo), createdFrom: one(query.createdFrom), createdTo: one(query.createdTo), sortBy: ['createdAt','orderNo','status','destinationCountry','channelId','estimatedFee'].includes(sortBy) ? sortBy : 'createdAt', sortDirection: sortDirection === 'ASC' ? 'ASC' : 'DESC', page: integer(query.page) ?? 1, pageSize: [20,50,100].includes(integer(query.pageSize) ?? 20) ? integer(query.pageSize)! : 20 }
}
export function toOrderRouteQuery(filter: OrderFilterState): LocationQueryRaw { const q: LocationQueryRaw = {}; const put=(key:string,value:unknown)=>{if(value!==undefined&&value!==null&&value!=='')q[key]=String(value)}; put('orderNo',filter.orderNo);put('storeId',filter.storeId);put('status',filter.status);put('workbenchFilter',filter.workbenchFilter);put('destinationCountry',filter.destinationCountry);put('channelId',filter.channelId);put('trackingNo',filter.trackingNo);put('createdFrom',filter.createdFrom);put('createdTo',filter.createdTo);put('sortBy',filter.sortBy);put('sortDirection',filter.sortDirection);put('page',filter.page);put('pageSize',filter.pageSize);return q }
export function toOrderApiQuery(filter: OrderFilterState): ShipmentOrderQuery { return { orderNo: filter.orderNo || undefined, storeId: filter.storeId, status: filter.status, workbenchFilter: filter.workbenchFilter, destinationCountry: filter.destinationCountry || undefined, channelId: id(filter.channelId), trackingNo: filter.trackingNo || undefined, createdFrom: filter.createdFrom || undefined, createdTo: filter.createdTo || undefined, sortBy: filter.sortBy, sortDirection: filter.sortDirection, page: filter.page, pageSize: filter.pageSize } }
