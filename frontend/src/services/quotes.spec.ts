import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { listQuotes } from './quotes'

describe('quote service contract', () => {
  afterEach(() => vi.restoreAllMocks())

  it('passes server-side filtering, paging and sorting unchanged to the quotes endpoint', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { data: { items: [], page: 2, pageSize: 50, total: 0, totalPages: 0 } } } as never)
    await listQuotes({ quoteNo: 'Q-1', storeId: '3', channelId: '5', destinationCountry: 'US', status: 'VALID', createdFrom: '2026-08-01T00:00', validTo: '2026-08-31T23:59', page: 2, pageSize: 50, sortField: 'amount', sortDirection: 'ASC' })
    expect(get).toHaveBeenCalledWith('/quotes', { params: expect.objectContaining({ quoteNo: 'Q-1', storeId: '3', channelId: '5', destinationCountry: 'US', status: 'VALID', page: 2, pageSize: 50, sortField: 'amount', sortDirection: 'ASC' }) })
  })
})
