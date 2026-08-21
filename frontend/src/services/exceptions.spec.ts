import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { addHandlingRecord, assignExceptionCase, financeConfirmClaim, listEvidence, resolveClaim, uploadEvidence } from './exceptions'

describe('exception handling and evidence service contracts', () => {
  afterEach(() => vi.restoreAllMocks())

  it('sends structured responsibility with assignment', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { data: { id: '11' } } } as never)
    await assignExceptionCase('11', { assignedToUserId: '8', responsibleParty: 'PROVIDER', version: 0 })
    expect(post).toHaveBeenCalledWith('/exceptions/11/assign', expect.objectContaining({ responsibleParty: 'PROVIDER' }), expect.anything())
  })

  it('uses idempotent JSON and multipart evidence paths', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { data: { id: '41' } } } as never)
    await addHandlingRecord('11', { recordType: 'FOLLOW_UP', content: 'provider contacted', version: 3 })
    const file = new Blob(['evidence'], { type: 'text/plain' }) as unknown as File
    Object.defineProperty(file, 'name', { value: 'proof.txt' })
    await uploadEvidence('11', file, 4, 'delivery proof')
    expect(post).toHaveBeenNthCalledWith(1, '/exceptions/11/handling-records', expect.anything(), expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.stringMatching(/^exception-handling-/) }) }))
    expect(post).toHaveBeenNthCalledWith(2, '/exceptions/11/evidence', expect.any(FormData), expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.stringMatching(/^exception-evidence-/) }) }))
    const form = post.mock.calls[1][1] as FormData
    expect(form.get('version')).toBe('4')
    expect(form.get('description')).toBe('delivery proof')
  })

  it('uses idempotency for claim result and finance confirmation', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { data: { id: '21' } } } as never)
    await resolveClaim('21', { status: 'PARTIALLY_APPROVED', reason: 'damage', approvedAmount: 4.5, resolvedAt: '2026-08-20T00:00:00Z', version: 2 })
    await financeConfirmClaim('21', { reason: 'checked', version: 3 })
    expect(post).toHaveBeenNthCalledWith(1, '/claims/21/result', expect.objectContaining({ approvedAmount: 4.5 }), expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.stringMatching(/^claim-result-/) }) }))
    expect(post).toHaveBeenNthCalledWith(2, '/claims/21/finance-confirmation', expect.anything(), expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.stringMatching(/^claim-finance-/) }) }))
  })

  it('keeps evidence list tenant-relative and avoids filename identity', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { data: [] } } as never)
    await listEvidence('11')
    expect(get).toHaveBeenCalledWith('/exceptions/11/evidence')
  })
})
