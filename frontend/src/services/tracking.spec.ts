import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { getShipmentTrackingTimeline, listTrackingEvents } from './tracking'

describe('tracking service contracts', () => {
  afterEach(() => vi.restoreAllMocks())

  it('loads paginated order tracking events through the tenant endpoint', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({
      data: { data: { page: 1, pageSize: 20, total: 1, totalPages: 1, items: [{ id: '1', eventId: 'EV-1' }] } },
    } as never)

    await expect(listTrackingEvents('9', { page: 1, pageSize: 20 })).resolves.toMatchObject({ total: 1 })
    expect(get).toHaveBeenCalledWith('/orders/9/tracking-events', { params: { page: 1, pageSize: 20 } })
  })

  it('preserves an empty page for the UI empty state', async () => {
    vi.spyOn(http, 'get').mockResolvedValue({
      data: { data: { page: 1, pageSize: 50, total: 0, totalPages: 0, items: [] } },
    } as never)

    await expect(listTrackingEvents('31', { page: 1, pageSize: 50 })).resolves.toMatchObject({ total: 0, items: [] })
  })

  it('queries the unified timeline by an encoded order or waybill reference', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({
      data: { data: [{ statusCode: 'SF_PICKED_UP', source: 'SF_EXPRESS' }] },
    } as never)

    await expect(getShipmentTrackingTimeline('UAT SF/001')).resolves.toHaveLength(1)
    expect(get).toHaveBeenCalledWith('/orders/UAT%20SF%2F001/tracking')
  })
})
