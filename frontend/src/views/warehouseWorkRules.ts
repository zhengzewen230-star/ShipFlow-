import type { WarehouseWorkItem } from '@/services/warehouse'

export const MAX_MEASUREMENT_VALUE = 1_000_000

export interface MeasurementDraft {
  actualWeight: number | null
  actualLength: number | null
  actualWidth: number | null
  actualHeight: number | null
}

const measurementFields: Array<keyof MeasurementDraft> = ['actualWeight', 'actualLength', 'actualWidth', 'actualHeight']

export function measurementValidationMessage(draft: MeasurementDraft) {
  for (const field of measurementFields) {
    const value = draft[field]
    if (value == null || !Number.isFinite(value) || value <= 0) return '复称重量和长宽高必须填写为大于 0 的数字。'
    if (value > MAX_MEASUREMENT_VALUE) return '复称重量和尺寸不能超过 1,000,000。'
    if (Math.abs(value * 1000 - Math.round(value * 1000)) > Number.EPSILON) return '复称重量和尺寸最多保留 3 位小数。'
  }
  return ''
}

export function hasCompleteMeasurement(item?: Pick<WarehouseWorkItem, 'actualWeight' | 'actualLength' | 'actualWidth' | 'actualHeight'>) {
  return item != null && measurementValidationMessage({
    actualWeight: item.actualWeight ?? null,
    actualLength: item.actualLength ?? null,
    actualWidth: item.actualWidth ?? null,
    actualHeight: item.actualHeight ?? null,
  }) === ''
}

export function canSaveMeasurement(item: WarehouseWorkItem | undefined, draft: MeasurementDraft, submitting: boolean) {
  return !submitting && item?.logisticsStatus === 'INBOUND' && measurementValidationMessage(draft) === ''
}

export function canCreateSfOrder(item: WarehouseWorkItem | undefined, submitting: boolean) {
  return !submitting && item?.logisticsStatus === 'READY_FOR_OUTBOUND' && hasCompleteMeasurement(item) && !item.sfTrackingNo
}
