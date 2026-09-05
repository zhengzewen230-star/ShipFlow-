import { describe, expect, it } from 'vitest'
import {
  exceptionListFilter,
  financeListFilter,
  orderListFilter,
  trackingFilter,
  warehouseListFilter,
  workbenchTargetLocation,
} from './workbenchTargetFilters'

describe('workbench target filters', () => {
  it('only accepts documented workbench destinations and query fields', () => {
    expect(workbenchTargetLocation({ route: '/app/orders', query: { status: 'IN_TRANSIT', storeId: 11, timeRange: 'TODAY', from: '2026-08-29T16:00:00Z', to: '2026-08-30T16:00:00Z', ignored: 'x' } }))
      .toEqual({ path: '/app/orders', query: { status: 'IN_TRANSIT', storeId: '11', timeRange: 'TODAY', from: '2026-08-29T16:00:00Z', to: '2026-08-30T16:00:00Z' } })
    expect(workbenchTargetLocation({ route: 'https://example.test', query: {} })).toBeUndefined()
  })

  it('restores orders and exceptions only with real API status values', () => {
    expect(orderListFilter({ resourceId: '35' })).toEqual({ lookupId: '35' })
    expect(orderListFilter({ status: 'PENDING_FEE_CONFIRMATION', storeId: '11' })).toEqual({ workbenchFilter: 'PENDING_FEE_CONFIRMATION', storeId: '11' })
    expect(orderListFilter({ status: 'MISSING_ADDRESS', storeId: 'bad' })).toEqual({ workbenchFilter: 'MISSING_ADDRESS', storeId: undefined })
    expect(exceptionListFilter({ status: 'PENDING_FINANCE_REVIEW' })).toEqual({ status: 'PENDING_FINANCE_CONFIRMATION' })
    expect(exceptionListFilter({ status: 'PENDING_EXCEPTION_FOLLOW_UP' })).toEqual({ workbenchFilter: 'PENDING_FOLLOW_UP' })
  })

  it('preserves unsupported warehouse and tracking aggregate filters without fabricating results', () => {
    expect(warehouseListFilter({ status: 'PENDING_INBOUND' })).toEqual({ status: 'PENDING_INBOUND' })
    expect(warehouseListFilter({ status: 'PENDING_LABEL' }).notice).toContain('接口')
    expect(trackingFilter({ reference: 'SO-31' })).toEqual({ reference: 'SO-31' })
    expect(trackingFilter({ status: 'TRACKING_EXCEPTION' }).notice).toContain('仅支持')
  })

  it('maps finance workbench categories to their supported real lists', () => {
    expect(financeListFilter({ status: 'PENDING_RECONCILIATION' })).toMatchObject({ tab: 'reconciliations', status: 'PENDING_CONFIRMATION', page: 1, pageSize: 20 })
    expect(financeListFilter({ status: 'BILL_IMPORT_ERRORS' })).toMatchObject({ tab: 'details', status: 'ERROR', page: 1, pageSize: 20 })
    expect(financeListFilter({ tab: 'details', status: 'ERROR', page: '3', pageSize: '50', recordId: '19' })).toEqual({ tab: 'details', status: 'ERROR', page: 3, pageSize: 50, recordId: '19' })
    expect(financeListFilter({ tab: ['details', 'audit'], status: 'ERROR' }).notice).toContain('聚合')
  })
})
