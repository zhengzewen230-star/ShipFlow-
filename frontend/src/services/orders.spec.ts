import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { confirmPrice, getPriceConfirmation, requestPriceConfirmation } from './orders'

describe('order price confirmation service contracts', () => {
  afterEach(() => vi.restoreAllMocks())

  it('loads the tenant-scoped confirmation projection and carries its version into a request', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({
      data: { data: { id: '9', orderNo: 'SO-9', quoteId: '8', storeId: '3', currentStatus: 'PENDING_PRICE_CONFIRMATION', estimatedFee: 10, currentFee: 12.5, confirmedFee: null, currency: 'CNY', chargeableWeight: 2, version: 4, feeAdjustmentId: '41', adjustmentType: 'INCREASE', beforeAmount: 10, afterAmount: 12.5, differenceAmount: 2.5, confirmationStatus: 'PENDING_CONFIRMATION', requestedAt: null, confirmedAt: null } } } as never)
    const post = vi.spyOn(http, 'post').mockResolvedValue({
      data: { data: { id: '9', orderNo: 'SO-9', quoteId: '8', storeId: '3', currentStatus: 'PENDING_PRICE_CONFIRMATION', estimatedFee: 10, currentFee: 12.5, confirmedFee: null, currency: 'CNY', chargeableWeight: 2, version: 4, feeAdjustmentId: '41', adjustmentType: 'INCREASE', beforeAmount: 10, afterAmount: 12.5, differenceAmount: 2.5, confirmationStatus: 'REQUESTED', requestedAt: '2026-08-17T00:00:00Z', confirmedAt: null } } } as never)

    await getPriceConfirmation('9')
    await requestPriceConfirmation('9', { feeAdjustmentId: '41', expectedFee: 12.5, version: 3 })

    expect(get).toHaveBeenCalledWith('/orders/9/price-confirmation')
    expect(post).toHaveBeenCalledWith('/orders/9/price-confirmation-requests', { feeAdjustmentId: '41', expectedFee: 12.5, version: 4 }, expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String), 'X-Request-ID': expect.any(String) }) }))
  })

  it('confirms through the final confirmation path and preserves the returned READY version', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({
      data: { data: { id: '10', orderNo: 'SO-10', quoteId: '8', storeId: '3', currentStatus: 'READY_FOR_OUTBOUND', estimatedFee: 10, currentFee: 12.5, confirmedFee: 12.5, currency: 'CNY', chargeableWeight: 2, version: 5, feeAdjustmentId: '42', adjustmentType: 'INCREASE', beforeAmount: 10, afterAmount: 12.5, differenceAmount: 2.5, confirmationStatus: 'CONFIRMED', requestedAt: null, confirmedAt: '2026-08-17T00:00:00Z' } } } as never)

    await expect(confirmPrice('10', { feeAdjustmentId: '42', expectedFee: 12.5, version: 4 })).resolves.toMatchObject({ currentStatus: 'READY_FOR_OUTBOUND', version: 5 })
    expect(post).toHaveBeenCalledWith('/orders/10/price-confirmation', { feeAdjustmentId: '42', expectedFee: 12.5, version: 4 }, expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.any(String) }) }))
  })
})
