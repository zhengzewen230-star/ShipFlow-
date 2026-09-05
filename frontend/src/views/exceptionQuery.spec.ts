import { describe, expect, it } from 'vitest'
import { defaultExceptionFilter, parseExceptionQuery, toExceptionApiQuery, toExceptionRouteQuery } from './exceptionQuery'

describe('exception list URL contract', () => {
  it('restores all supported filters and stable sorting from a deep link', () => {
    const state = parseExceptionQuery({
      exceptionType: 'TRANSPORT', status: 'PROCESSING', orderNo: 'SO-100', storeId: '8',
      responsibleParty: 'PROVIDER', createdFrom: '2026-08-01T08:00', createdTo: '2026-08-02T08:00',
      page: '3', pageSize: '50', sortBy: 'updatedAt', sortDirection: 'ASC',
    })
    expect(state).toMatchObject({ exceptionType: 'TRANSPORT', status: 'PROCESSING', orderNo: 'SO-100', storeId: '8', responsibleParty: 'PROVIDER', page: 3, pageSize: 50, sortBy: 'updatedAt', sortDirection: 'ASC' })
    expect(toExceptionApiQuery(state)).toMatchObject({ orderNo: 'SO-100', storeId: '8', page: 3, pageSize: 50 })
  })

  it('rejects arbitrary status, sort and identifier query values', () => {
    const state = parseExceptionQuery({ status: 'DROP TABLE', sortBy: 'tenant_id desc', sortDirection: 'SIDEWAYS', storeId: '-1', pageSize: '999' })
    expect(state).toEqual(defaultExceptionFilter)
  })

  it('keeps list state and selected detail in the route', () => {
    const route = toExceptionRouteQuery({ ...defaultExceptionFilter, orderNo: 'SO-9', page: 2 }, '42')
    expect(route).toMatchObject({ orderNo: 'SO-9', page: '2', pageSize: '20', sortBy: 'createdAt', sortDirection: 'DESC', exceptionId: '42' })
  })
})
