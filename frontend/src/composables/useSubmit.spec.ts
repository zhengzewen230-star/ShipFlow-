import { describe, expect, it } from 'vitest'
import { ApiError } from '@/types/api'
import { useSubmit } from './useSubmit'

describe('useSubmit', () => {
  it('shows the server business error instead of leaving an order submission pending', async () => {
    const submit = useSubmit()

    await submit.submit(async () => {
      throw new ApiError('报价条件或报价快照不完整，无法创建订单', 422, 'ORDER-1003')
    })

    expect(submit.submitting.value).toBe(false)
    expect(submit.errorMessage.value).toBe('报价条件或报价快照不完整，无法创建订单')
    expect(submit.errorCode.value).toBe('ORDER-1003')
  })

  it('keeps the backend trace id for failed writes and blocks duplicate execution', async () => {
    const submit = useSubmit()
    let calls = 0
    let resolveAction: (() => void) | undefined
    const pending = new Promise<void>(resolve => { resolveAction = resolve })
    const first = submit.submit(async () => { calls += 1; await pending; throw new ApiError('并发冲突，请刷新后重试。', 409, 'COMMON-1005', 'trace-write-1') })
    const second = await submit.submit(async () => { calls += 1 })
    expect(second).toBeUndefined()
    expect(calls).toBe(1)
    resolveAction?.()
    await first
    expect(submit.errorTraceId.value).toBe('trace-write-1')
  })
})
