import { reactive } from 'vue'

export interface ConfirmActionOptions {
  title: string
  description: string
  confirmLabel?: string
  cancelLabel?: string
  danger?: boolean
}

const state = reactive({
  open: false,
  title: '',
  description: '',
  confirmLabel: '确认',
  cancelLabel: '取消',
  danger: false,
})

let resolver: ((confirmed: boolean) => void) | undefined

function finish(confirmed: boolean) {
  state.open = false
  resolver?.(confirmed)
  resolver = undefined
}

export function useConfirmAction() {
  function confirm(options: ConfirmActionOptions) {
    if (resolver) resolver(false)
    Object.assign(state, {
      open: true,
      title: options.title,
      description: options.description,
      confirmLabel: options.confirmLabel ?? '确认',
      cancelLabel: options.cancelLabel ?? '取消',
      danger: options.danger ?? false,
    })
    return new Promise<boolean>(resolve => { resolver = resolve })
  }

  return { state, confirm, accept: () => finish(true), cancel: () => finish(false) }
}
