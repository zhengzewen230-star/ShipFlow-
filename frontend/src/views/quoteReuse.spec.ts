import { describe, expect, it } from 'vitest'
import type { Quote } from '@/services/quotes'
import { quoteReuseInput } from './quoteReuse'

describe('quote reuse input', () => {
  it('copies only business input and excludes calculated quote output', () => {
    const quote = { id: '8', quoteNo: 'Q-8', storeId: '3', channelId: '5', destinationCountry: 'US', ruleVersionNo: 7, declaredWeight: 2.1, declaredLength: 10, declaredWidth: 20, declaredHeight: 30, declaredVolumeWeight: 1.2, declaredChargeableWeight: 2.1, amount: 99.99, currency: 'USD', feeDetail: { priceRuleId: '44', ruleVersionNo: 7, tierNo: 2 }, validFrom: '2026-08-01T00:00:00Z', validTo: '2026-08-02T00:00:00Z', status: 'VALID', version: 4 } satisfies Quote
    expect(quoteReuseInput(quote)).toEqual({ storeId: '3', channelId: '5', destinationCountry: 'US', weight: '2.1', length: '10', width: '20', height: '30' })
    expect(quoteReuseInput(quote)).not.toHaveProperty('amount')
    expect(quoteReuseInput(quote)).not.toHaveProperty('validTo')
    expect(quoteReuseInput(quote)).not.toHaveProperty('ruleVersionNo')
  })
})
