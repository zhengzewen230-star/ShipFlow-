import { describe, expect, it } from 'vitest'
import { parseStoreQuery, toStoreQuery, type StoreFilterState } from './storeQuery'

describe('store query contract', () => {
  it('restores only whitelisted filters, paging and sorting', () => {
    expect(parseStoreQuery({
      storeCode: ' S1 ', storeName: 'Demo', platformCode: 'AMAZON', status: 'ACTIVE',
      page: '2', pageSize: '50', sortBy: 'storeName', sortDirection: 'ASC', ignored: 'x',
    })).toEqual({ storeCode: 'S1', storeName: 'Demo', platformCode: 'AMAZON', status: 'ACTIVE', page: 2, pageSize: 50, sortBy: 'storeName', sortDirection: 'ASC' })
  })

  it('falls back safely for invalid query values', () => {
    expect(parseStoreQuery({ status: 'PENDING', page: '0', pageSize: '1000', sortBy: 'tenantId', sortDirection: 'DROP' }))
      .toEqual({ storeCode: '', storeName: '', platformCode: '', status: '', page: 1, pageSize: 20, sortBy: 'updatedAt', sortDirection: 'DESC' })
  })

  it('serializes empty defaults away and preserves meaningful filters', () => {
    const state: StoreFilterState = { storeCode: 'S1', storeName: '', platformCode: 'AMAZON', status: '', page: 1, pageSize: 20, sortBy: 'updatedAt', sortDirection: 'DESC' }
    expect(toStoreQuery(state)).toEqual({ storeCode: 'S1', platformCode: 'AMAZON' })
  })
})
