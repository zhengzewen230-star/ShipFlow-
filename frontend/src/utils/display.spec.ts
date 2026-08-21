import { describe, expect, it } from 'vitest'
import { displayValue, formatChargeableWeight } from './display'

describe('displayValue', () => {
  it('formats quote validity timestamps in China Standard Time', () => {
    expect(displayValue('validTo', '2026-08-14T13:55:32.277Z')).toBe('2026-08-14 21:55:32')
  })

  it('keeps an invalid date string visible instead of changing it', () => {
    expect(displayValue('validTo', 'not-a-date')).toBe('not-a-date')
  })

  it('converts chargeable weight from grams to kilograms', () => {
    expect(formatChargeableWeight(5400)).toBe('5.40 kg')
    expect(displayValue('chargeableWeight', 5400)).toBe('5.40 kg')
  })

  it('keeps empty and invalid chargeable weights safe', () => {
    expect(formatChargeableWeight(null)).toBe('—')
    expect(formatChargeableWeight('unknown')).toBe('unknown')
  })
})
