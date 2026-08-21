import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { changeStoreStatus, getDefaultStoreAddress, getStore, listStoreLogisticsChannels, setDefaultStoreLogisticsChannel, updateDefaultStoreAddress, updateStore } from './stores'

describe('store detail service contract', () => {
  afterEach(() => vi.restoreAllMocks())

  it('requests the scoped detail endpoint and keeps masked fields only', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { data: {
      id: '10', tenantId: '1', storeCode: 'S1', storeName: 'Store', platformCode: 'AMAZON', platformAccountMasked: 'a**t',
      countryRegion: null, defaultShippingAddress: null, defaultLogisticsChannel: null, status: 'ACTIVE', version: 0,
      createdAt: null, updatedAt: null, historicalOrderCount: 3, auditSummary: [],
      unavailableFields: ['countryRegion', 'defaultShippingAddress', 'defaultLogisticsChannel'], configurationVersion: 0,
    } } } as never)
    const detail = await getStore('10')
    expect(get).toHaveBeenCalledWith('/stores/10')
    expect(detail.platformAccountMasked).toBe('a**t')
    expect(detail).not.toHaveProperty('platformAccount')
    expect(detail.historicalOrderCount).toBe(3)
  })

  it('sends versioned store updates with a generated idempotency key', async () => {
    const put = vi.spyOn(http, 'put').mockResolvedValue({ data: { data: { id: '10' } } } as never)
    await updateStore('10', { storeName: 'New', platformCode: 'AMAZON', platformAccount: 'acct', version: 2 })
    expect(put).toHaveBeenCalledWith('/stores/10', expect.objectContaining({ storeName: 'New', version: 2 }), expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }) }))
  })

  it('sends versioned status changes through the real endpoint', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { data: { id: '10' } } } as never)
    await changeStoreStatus('10', { status: 'DISABLED', version: 2 })
    expect(post).toHaveBeenCalledWith('/stores/10/status', { status: 'DISABLED', version: 2 }, expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }) }))
  })

  it('reads the default address and logistics channels from the scoped resource endpoints', async () => {
    const get = vi.spyOn(http, 'get').mockImplementation(async (url) => ({ data: { data: url.endsWith('default-address') ? null : [] } }) as never)
    expect(await getDefaultStoreAddress('10')).toBeNull()
    expect(await listStoreLogisticsChannels('10')).toEqual([])
    expect(get).toHaveBeenNthCalledWith(1, '/stores/10/default-address')
    expect(get).toHaveBeenNthCalledWith(2, '/stores/10/logistics-channels')
  })

  it('writes versioned resource changes with idempotency keys and keeps sensitive fields out of service input', async () => {
    const put = vi.spyOn(http, 'put').mockResolvedValue({ data: { data: { id: '21', phone: '13****78', email: 'a***@example.com' } } } as never)
    await updateDefaultStoreAddress('10', {
      addressCode: 'A1', contactName: '联系人', companyName: null, phone: '13****78', email: 'a***@example.com',
      countryCode: 'CN', stateProvince: null, city: '上海', district: null, addressLine1: '浦东', addressLine2: null, postalCode: '200000', version: 0,
    })
    await setDefaultStoreLogisticsChannel('10', { channelId: '41', version: 0 })
    expect(put).toHaveBeenNthCalledWith(1, '/stores/10/default-address', expect.objectContaining({ version: 0 }), expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }) }))
    expect(put).toHaveBeenNthCalledWith(2, '/stores/10/default-logistics-channel', { channelId: '41', version: 0 }, expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }) }))
    expect(put.mock.calls[0][1]).not.toHaveProperty('platformAccount')
  })
})
