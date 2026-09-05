import { describe, expect, it } from 'vitest'
import { useConfirmAction } from './useConfirmAction'

describe('useConfirmAction', () => {
  it('requires an explicit decision for high-risk operations', async () => {
    const dialog = useConfirmAction()
    const decision = dialog.confirm({ title: '确认关闭异常', description: '关闭后不能继续修改。', danger: true })
    expect(dialog.state).toMatchObject({ open: true, title: '确认关闭异常', description: '关闭后不能继续修改。', danger: true })
    dialog.accept()
    await expect(decision).resolves.toBe(true)
    expect(dialog.state.open).toBe(false)
  })

  it('cancels a previous unresolved confirmation before showing another one', async () => {
    const dialog = useConfirmAction()
    const first = dialog.confirm({ title: '第一项', description: '第一项说明' })
    const second = dialog.confirm({ title: '第二项', description: '第二项说明' })
    await expect(first).resolves.toBe(false)
    dialog.cancel()
    await expect(second).resolves.toBe(false)
  })
})
