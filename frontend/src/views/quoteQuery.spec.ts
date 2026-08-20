import { describe, expect, it } from 'vitest'
import { parseQuoteQuery, toQuoteApiQuery, toQuoteRouteQuery } from './quoteQuery'

describe('quote query contract', () => {
  it('restores whitelisted filters, paging and sorting', () => {
    expect(parseQuoteQuery({ quoteNo: ' Q-1 ', storeId: '3', channelId: '8', destinationCountry: 'us', status: 'VALID', createdFrom: '2026-08-01T00:00:00Z', validTo: '2026-08-31T00:00:00Z', page: '2', pageSize: '50', sortField: 'amount', sortDirection: 'ASC' }))
      .toEqual({ quoteNo: 'Q-1', storeId: '3', channelId: '8', destinationCountry: 'US', status: 'VALID', createdFrom: '2026-08-01T00:00:00Z', createdTo: '', validFrom: '', validTo: '2026-08-31T00:00:00Z', page: 2, pageSize: 50, sortField: 'amount', sortDirection: 'ASC' })
  })

  it('rejects unsupported and malformed values before they reach the API', () => {
    expect(parseQuoteQuery({ storeId: '0', channelId: '-1', destinationCountry: 'USA', status: 'DRAFT', createdFrom: 'not-a-date', page: '0', pageSize: '101', sortField: 'ruleVersionNo', sortDirection: 'DROP' }))
      .toEqual({ quoteNo: '', storeId: undefined, channelId: undefined, destinationCountry: '', status: '', createdFrom: '', createdTo: '', validFrom: '', validTo: '', page: 1, pageSize: 20, sortField: 'createdAt', sortDirection: 'DESC' })
  })

  it('keeps only meaningful state in the route while retaining API paging defaults', () => {
    const state = parseQuoteQuery({ quoteNo: 'Q-1', destinationCountry: 'US' })
    expect(toQuoteRouteQuery(state)).toEqual({ quoteNo: 'Q-1', destinationCountry: 'US' })
    expect(toQuoteApiQuery(state)).toEqual(expect.objectContaining({ quoteNo: 'Q-1', destinationCountry: 'US', page: 1, pageSize: 20, sortField: 'createdAt', sortDirection: 'DESC' }))
  })
})
