import { describe, expect, it } from 'vitest'
import { parseStoreId, storeDetailUnavailable, storeListBackQuery } from './storeDetail'

describe('store detail navigation contract', () => {
  it('accepts only positive numeric resource ids', () => {
    expect(parseStoreId('10')).toBe('10')
    expect(parseStoreId('0')).toBeUndefined()
    expect(parseStoreId('10/../11')).toBeUndefined()
  })

  it('distinguishes unavailable backend fields from unset fields', () => {
    expect(storeDetailUnavailable(['countryRegion'], 'countryRegion')).toBe('功能暂不可用')
    expect(storeDetailUnavailable(['countryRegion'], 'defaultShippingAddress')).toBe('尚未配置')
  })

  it('preserves meaningful list query values when returning', () => {
    expect(storeListBackQuery({ storeCode: 'S1', page: '2', empty: '', ignored: 'drop', sortBy: 'storeName' })).toEqual({ storeCode: 'S1', page: '2', sortBy: 'storeName' })
  })
})
