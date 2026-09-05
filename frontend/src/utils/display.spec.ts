import { describe, expect, it, vi } from 'vitest'
import { displayEnumOption, displayLabel, displayValue, formatBillingErrorReason, formatChargeableWeight, formatMoney, getUnknownEnumDiagnostics } from './display'

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

  it('maps exception, billing, store and order enumerations to Chinese', () => {
    expect(displayValue('exceptionType', 'TRANSPORT')).toBe('运输')
    expect(displayValue('responsibleParty', 'PROVIDER')).toBe('物流商')
    expect(displayValue('status', 'PENDING_FINANCE_CONFIRMATION')).toBe('待财务确认')
    expect(displayValue('status', 'PARTIAL_SUCCESS')).toBe('部分成功')
    expect(displayValue('platformCode', 'MARKETPLACE_A')).toBe('平台 A')
    expect(displayValue('status', 'READY_FOR_OUTBOUND')).toBe('待出库')
    expect(displayLabel('duplicateCount')).toBe('重复行数')
    expect(displayEnumOption('WAITING_PROVIDER_FEEDBACK')).toBe('待物流商反馈')
    expect(displayEnumOption('fee_adjustment')).toBe('费用调整')
  })

  it('keeps unknown enum values out of the rendered UI and records diagnostics', () => {
    const warning = vi.spyOn(console, 'warn').mockImplementation(() => undefined)
    expect(displayValue('status', 'UNRECOGNIZED_STATUS')).toBe('未知状态')
    expect(displayValue('exceptionType', 'UNRECOGNIZED_TYPE')).toBe('未配置')
    expect(getUnknownEnumDiagnostics()).toContain('status:UNRECOGNIZED_STATUS')
    warning.mockRestore()
  })

  it('formats money consistently without changing API values', () => {
    expect(formatMoney('1234.5', 'USD')).toBe('USD 1,234.50')
    expect(formatMoney('not-a-number', 'USD')).toBe('未配置')
  })

  it('renders billing fee types, handling statuses and known import errors in Chinese', () => {
    expect(displayValue('feeType', 'FREIGHT')).toBe('运费')
    expect(displayValue('feeType', 'INVALID')).toBe('无效费用类型')
    expect(displayValue('errorHandlingStatus', 'PENDING')).toBe('待处理')
    expect(displayValue('errorHandlingStatus', 'IGNORED')).toBe('已忽略')
    expect(formatBillingErrorReason('Duplicate provider bill detail number: P1***04')).toBe('物流商账单明细号重复：P1***04')
    expect(formatBillingErrorReason('Invalid billed amount')).toBe('账单金额无效')
    expect(formatBillingErrorReason('Shipment order not found for provider tracking number')).toBe('未找到对应物流单号的订单')
    expect(formatBillingErrorReason('Bill currency does not match order currency')).toBe('账单币种与订单币种不一致')
  })
})
