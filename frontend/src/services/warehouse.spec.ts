import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { confirmInbound, confirmOutbound, executeSfOperation, getWarehouseOverview, listWarehouseWork, sfOperationLabel, submitMeasurement } from './warehouse'

describe('warehouse service contracts', () => {
  afterEach(() => vi.restoreAllMocks())

  it('sends only the backend measurement fields and reads the new version', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { data: { orderId: '9', status: 'READY_FOR_OUTBOUND', chargeableWeight: 2, currentFee: 40, version: 4 } } } as never)
    await expect(submitMeasurement('9', { actualWeight: 1, actualLength: 10, actualWidth: 10, actualHeight: 10, version: 3 })).resolves.toMatchObject({ version: 4 })
    expect(post).toHaveBeenCalledWith('/orders/9/measurements', { actualWeight: 1, actualLength: 10, actualWidth: 10, actualHeight: 10, version: 3 }, expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }) }))
  })

  it('uses the warehouse result contract for inbound and outbound', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { data: { orderId: '9', status: 'INBOUND', chargeableWeight: 1, currentFee: 20, version: 2 } } } as never)
    await confirmInbound('9', 1)
    await confirmOutbound('9', { trackingNo: 'SF-TEST', version: 2 })
    expect(post).toHaveBeenNthCalledWith(1, '/orders/9/inbound', { version: 1 }, expect.any(Object))
    expect(post).toHaveBeenNthCalledWith(2, '/orders/9/outbound', { trackingNo: 'SF-TEST', version: 2 }, expect.any(Object))
  })

  it('loads the tenant-scoped warehouse overview endpoint', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({
      data: { data: {
        pendingInbound: 1,
        pendingMeasurement: 2,
        pendingLabel: 0,
        pendingHandover: 0,
        pendingOutbound: 3,
        inTransit: 4,
        trackingExceptions: 0,
        todayInbound: 1,
        todayOutbound: 0,
        recentOrders: [],
        recentTrackingExceptions: [],
      } },
    } as never)

    await expect(getWarehouseOverview()).resolves.toMatchObject({ pendingInbound: 1, recentOrders: [] })
    expect(get).toHaveBeenCalledWith('/warehouse/overview')
  })

  it('loads the warehouse work projection with status and order filters', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { data: { page: 1, pageSize: 20, totalPages: 1, total: 0, items: [] } } } as never)
    await expect(listWarehouseWork({ status: 'INBOUND', orderNo: 'TEST-ORDER', page: 1, pageSize: 20 })).resolves.toMatchObject({ total: 0 })
    expect(get).toHaveBeenCalledWith('/warehouse/orders', { params: { status: 'INBOUND', orderNo: 'TEST-ORDER', page: 1, pageSize: 20 } })
  })

  it('routes supplier operations through the backend with an idempotency key', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { data: { operation: 'CREATE_ORDER', serviceCode: 'COM_RECE_IUOP_CREATE_ORDER', requestId: 'sf-test', status: 'SUBMITTED' } } } as never)
    await expect(executeSfOperation('31', 'CREATE_ORDER')).resolves.toMatchObject({ requestId: 'sf-test' })
    expect(post).toHaveBeenCalledWith('/orders/31/sf-international/CREATE_ORDER', { msgData: '' }, expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }) }))
    expect(sfOperationLabel('UPLOAD_CERTIFY')).toBe('清关资料上传')
  })
})
