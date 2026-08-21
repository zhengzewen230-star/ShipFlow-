import type { LocationQuery } from 'vue-router'
import { parseStoreQuery, toStoreQuery } from './storeQuery'

export function parseStoreId(value: unknown): string | undefined {
  const normalized = typeof value === 'string' ? value.trim() : ''
  return /^\d+$/.test(normalized) && BigInt(normalized) > 0n ? normalized : undefined
}

export function storeDetailUnavailable(unavailableFields: string[], field: string) {
  return unavailableFields.includes(field) ? '功能暂不可用' : '尚未配置'
}

export function storeListBackQuery(query: LocationQuery) {
  return toStoreQuery(parseStoreQuery(query))
}
