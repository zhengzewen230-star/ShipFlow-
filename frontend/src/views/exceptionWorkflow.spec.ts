import { describe, expect, it } from 'vitest'
import { allowedExceptionTransitions, canMutateException, idList } from './exceptionWorkflow'

describe('exception workflow', () => {
  it('enforces the strict exception state graph', () => {
    expect(allowedExceptionTransitions('PENDING_FINANCE_CONFIRMATION')).toEqual(['RESOLVED'])
    expect(allowedExceptionTransitions('RESOLVED')).toEqual(['CLOSED'])
    expect(allowedExceptionTransitions('CLOSED')).toEqual([])
  })
  it('disables closed mutations and deduplicates evidence ids', () => {
    expect(canMutateException('CLOSED')).toBe(false)
    expect(idList('41, 42,41')).toEqual(['41', '42'])
  })
})
