import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import { addHandlingRecord, assignExceptionCase, listEvidence, uploadEvidence } from './exceptions'

describe('exception handling and evidence service contracts', () => {
  afterEach(() => vi.restoreAllMocks())

  it('sends structured responsibility with assignment', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { data: { id: '11' } } } as never)
    await assignExceptionCase('11', { assignedToUserId: '8', responsibleParty: 'PROVIDER', version: 0 })
    expect(post).toHaveBeenCalledWith('/exceptions/11/assign', expect.objectContaining({ responsibleParty: 'PROVIDER' }), expect.anything())
  })

  it('uses idempotent JSON and multipart evidence paths', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { data: { id: '41' } } } as never)
    await addHandlingRecord('11', { recordType: 'FOLLOW_UP', content: 'provider contacted' })
    const file = new Blob(['evidence'], { type: 'text/plain' }) as unknown as File
    Object.defineProperty(file, 'name', { value: 'proof.txt' })
    await uploadEvidence('11', file)
    expect(post).toHaveBeenNthCalledWith(1, '/exceptions/11/handling-records', expect.anything(), expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.stringMatching(/^exception-handling-/) }) }))
    expect(post).toHaveBeenNthCalledWith(2, '/exceptions/11/evidence', expect.any(FormData), expect.objectContaining({ headers: expect.objectContaining({ 'Idempotency-Key': expect.stringMatching(/^exception-evidence-/) }) }))
  })

  it('keeps evidence list tenant-relative and avoids filename identity', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { data: [] } } as never)
    await listEvidence('11')
    expect(get).toHaveBeenCalledWith('/exceptions/11/evidence')
  })
})
