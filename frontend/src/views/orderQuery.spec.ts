import { describe, expect, it } from 'vitest'
import { parseOrderQuery, toOrderApiQuery } from './orderQuery'

describe('order query state', () => {
  it('restores whitelisted order filters from the URL', () => {
    const filter = parseOrderQuery({ orderNo: 'SO-1', storeId: '3', status: 'DRAFT', destinationCountry: 'US', channelId: '2', trackingNo: 'SF-1', sortBy: 'orderNo', sortDirection: 'ASC', page: '2', pageSize: '50' })
    expect(toOrderApiQuery(filter)).toMatchObject({ orderNo: 'SO-1', storeId: '3', status: 'DRAFT', destinationCountry: 'US', channelId: '2', trackingNo: 'SF-1', sortBy: 'orderNo', sortDirection: 'ASC', page: 2, pageSize: 50 })
  })

  it('falls back for invalid pagination and sort values', () => {
    const filter = parseOrderQuery({ sortBy: 'drop table', sortDirection: 'sideways', page: '0', pageSize: '1000' })
    expect(filter).toMatchObject({ sortBy: 'createdAt', sortDirection: 'DESC', page: 1, pageSize: 20 })
  })
})
