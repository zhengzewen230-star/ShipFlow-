import { afterEach, describe, expect, it, vi } from 'vitest'
import { ApiError } from '@/types/api'
import { http } from '@/services/http'
import { getOperationsWorkbench, type OperationsWorkbench } from '@/services/operations'
import {
  classifyWorkbenchError,
  copyWorkbenchTraceId,
  createOperationsWorkbenchLoader,
  formatMoney,
  formatShanghaiDateTime,
  formatWeightKg,
  isOperationsWorkbenchEmpty,
  workbenchTargetLocation,
} from './operationsWorkbench'

const window = { preset: 'TODAY' as const, from: '2026-08-16T16:00:00Z', to: '2026-08-17T15:59:59.999Z' }
const metricKeys = ['PENDING_ORDERS', 'PENDING_INBOUND', 'PENDING_MEASUREMENT', 'PENDING_LABEL', 'PENDING_OUTBOUND', 'IN_TRANSIT', 'TRACKING_EXCEPTION', 'PENDING_FINANCE']
const todoKeys = ['PENDING_FEE_CONFIRMATION', 'MISSING_ADDRESS', 'MISSING_CUSTOMS_DOCUMENT', 'PENDING_WAREHOUSE', 'PENDING_EXCEPTION_FOLLOW_UP', 'PENDING_RECONCILIATION']

function snapshot(overrides: Partial<OperationsWorkbench> = {}): OperationsWorkbench {
  return {
    businessTimeZone: 'Asia/Shanghai',
    scope: { tenantId: '7', storeIds: ['11'], scopeType: 'AUTHORIZED_STORES' },
    timeRange: window,
    refreshedAt: '2026-08-17T02:30:00Z',
    metrics: metricKeys.map((key, index) => ({
      key, label: key, count: index === 0 ? 2 : 0, window, refreshedAt: '2026-08-17T02:30:00Z',
      target: { route: '/app/orders', query: { status: key, storeId: '11' }, resourceType: 'ORDER' }, breakdown: [],
    })),
    todos: todoKeys.map(key => ({
      key, label: key, count: 0, window,
      target: { route: '/app/orders', query: { todo: key }, resourceType: 'ORDER' },
    })),
    recentOrders: [{
      orderId: '31', orderNo: 'SO-31', storeId: '11', storeName: '主店铺', destination: 'US',
      orderStatus: 'READY_FOR_OUTBOUND', chargeableWeight: '5.400', estimatedFee: '20.00', currency: 'USD',
      sfTrackingNo: null, updatedAt: '2026-08-17T01:00:00Z', nextAction: '确认出库',
      target: { route: '/app/orders', query: { orderId: '31' }, resourceType: 'ORDER' },
    }],
    risks: [],
    ...overrides,
  }
}

describe('merchant operations workbench frontend contract', () => {
  afterEach(() => vi.restoreAllMocks())

  it('loads the backend snapshot through the documented endpoint', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { data: snapshot() } } as never)

    await expect(getOperationsWorkbench({ timeRange: 'TODAY', recentLimit: 10, riskLimit: 10 })).resolves.toMatchObject({
      businessTimeZone: 'Asia/Shanghai',
      scope: { tenantId: '7', storeIds: ['11'] },
    })
    expect(get).toHaveBeenCalledWith('/operations/workbench', { params: { timeRange: 'TODAY', recentLimit: 10, riskLimit: 10 } })
  })

  it('exposes a successful snapshot and recognizes a real empty snapshot', async () => {
    const fetcher = vi.fn().mockResolvedValue(snapshot())
    const loader = createOperationsWorkbenchLoader(fetcher)
    await expect(loader.load({ timeRange: 'TODAY' })).resolves.toMatchObject({ refreshedAt: '2026-08-17T02:30:00Z' })

    const empty = snapshot({
      metrics: metricKeys.map(key => ({ ...snapshot().metrics[0], key, label: key, count: 0 })),
      todos: todoKeys.map(key => ({ ...snapshot().todos[0], key, label: key, count: 0 })),
      recentOrders: [], risks: [],
    })
    expect(isOperationsWorkbenchEmpty(empty)).toBe(true)
  })

  it('classifies session, permission, server and network failures for retry UI', () => {
    expect(classifyWorkbenchError(new ApiError('expired', 401)).kind).toBe('unauthorized')
    expect(classifyWorkbenchError(new ApiError('forbidden', 403)).kind).toBe('forbidden')
    expect(classifyWorkbenchError(new ApiError('server', 500)).kind).toBe('server')
    expect(classifyWorkbenchError(new Error('network down')).kind).toBe('network')
  })

  it('keeps the backend trace id available for the failure state and copies it only when present', async () => {
    expect(classifyWorkbenchError(new ApiError('server', 500, 'COMMON-1007', 'trace-p305')).traceId).toBe('trace-p305')
    const writeText = vi.fn().mockResolvedValue(undefined)
    await expect(copyWorkbenchTraceId('trace-p305', writeText)).resolves.toBe(true)
    expect(writeText).toHaveBeenCalledWith('trace-p305')
    await expect(copyWorkbenchTraceId(undefined, writeText)).resolves.toBe(false)
    await expect(copyWorkbenchTraceId('trace-p305', vi.fn().mockRejectedValue(new Error('denied')))).resolves.toBe(false)
  })

  it('keeps metric navigation within backend-provided app routes and filters empty query values', () => {
    expect(workbenchTargetLocation({
      route: '/app/orders', query: { status: 'PENDING_INBOUND', storeId: '11', empty: '', missing: null, unexpected: 'ignored' }, resourceType: 'ORDER',
    })).toEqual({ path: '/app/orders', query: { status: 'PENDING_INBOUND', storeId: '11' } })
    expect(workbenchTargetLocation({ route: 'https://example.test', query: {}, resourceType: 'ORDER' })).toBeUndefined()
  })

  it('deduplicates repeated refreshes while the same request is in flight', async () => {
    let resolveRequest!: (value: OperationsWorkbench) => void
    const pending = new Promise<OperationsWorkbench>(resolve => { resolveRequest = resolve })
    const fetcher = vi.fn().mockReturnValue(pending)
    const loader = createOperationsWorkbenchLoader(fetcher)
    const first = loader.load({ timeRange: 'TODAY' })
    const second = loader.load({ timeRange: 'TODAY' })

    expect(second).toBe(first)
    expect(fetcher).toHaveBeenCalledTimes(1)
    resolveRequest(snapshot())
    await expect(first).resolves.toMatchObject({ scope: { tenantId: '7' } })
  })

  it('formats UTC response values for Asia/Shanghai without changing backend units', () => {
    expect(formatShanghaiDateTime('2026-08-17T00:30:00Z')).toContain('2026-08-17 08:30:00')
    expect(formatWeightKg('5.4')).toBe('5.400 kg')
    expect(formatMoney('20', 'USD')).toBe('USD 20.00')
  })
})
