import { describe, expect, it } from 'vitest'
import { canCreateSfOrder, canSaveMeasurement, measurementValidationMessage } from './warehouseWorkRules'
import type { WarehouseWorkItem } from '@/services/warehouse'

const draft = { actualWeight: 2, actualLength: 10, actualWidth: 20, actualHeight: 5 }

function item(status: WarehouseWorkItem['logisticsStatus'], measured = false): WarehouseWorkItem {
  return {
    id: '31', businessOrderNo: 'SO-31', tenantId: '7', tenantName: '测试租户', destinationCountry: 'US',
    declaredWeight: 1, declaredLength: 10, declaredWidth: 10, declaredHeight: 10, declaredVolumeWeight: 1,
    actualWeight: measured ? draft.actualWeight : null, actualLength: measured ? draft.actualLength : null,
    actualWidth: measured ? draft.actualWidth : null, actualHeight: measured ? draft.actualHeight : null,
    actualVolumeWeight: measured ? 0.2 : null, chargeableWeight: 2, estimatedFee: 20, currentFee: 20,
    currency: 'USD', feeDifference: 0, feeAlert: false, warehouseStatus: status, logisticsStatus: status,
    version: measured ? 4 : 3, createdAt: '2026-08-16T00:00:00Z',
  }
}

describe('warehouse work button rules', () => {
  it('allows a valid re-weigh save and enables SF creation only after refreshed ready data', () => {
    expect(canSaveMeasurement(item('INBOUND'), draft, false)).toBe(true)
    expect(canCreateSfOrder(item('INBOUND'), false)).toBe(false)
    expect(canCreateSfOrder(item('READY_FOR_OUTBOUND', true), false)).toBe(true)
  })

  it('rejects missing, non-positive, excessive and over-precision measurements', () => {
    expect(measurementValidationMessage({ ...draft, actualWeight: null })).toContain('大于 0')
    expect(measurementValidationMessage({ ...draft, actualWeight: 0 })).toContain('大于 0')
    expect(measurementValidationMessage({ ...draft, actualWeight: 1_000_000.001 })).toContain('1,000,000')
    expect(measurementValidationMessage({ ...draft, actualWeight: 1.2345 })).toContain('3 位小数')
  })

  it('keeps SF creation disabled when a re-weigh save failed or is still incomplete', () => {
    const inbound = item('INBOUND')
    expect(canCreateSfOrder(inbound, false)).toBe(false)
    expect(canCreateSfOrder(inbound, true)).toBe(false)
    expect(canCreateSfOrder(item('READY_FOR_OUTBOUND'), false)).toBe(false)
  })
})
