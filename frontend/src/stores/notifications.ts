import { ref } from 'vue'
import { defineStore } from 'pinia'
export type NoticeKind = 'success' | 'error' | 'info'
export interface Notice { id: number; kind: NoticeKind; message: string; code?: string; traceId?: string }
export const useNotificationStore = defineStore('notifications', () => {
  const notices = ref<Notice[]>([])
  let nextId = 1
  function remove(id: number) { notices.value = notices.value.filter(item => item.id !== id) }
  function push(message: string, kind: NoticeKind = 'info', meta: Pick<Notice, 'code' | 'traceId'> = {}) {
    const notice = { id: nextId++, kind, message, ...meta }
    notices.value.push(notice)
    if (typeof window !== 'undefined') window.setTimeout(() => remove(notice.id), kind === 'error' ? 8_000 : 4_500)
    return notice.id
  }
  return { notices, push, remove }
})
