import { ref } from 'vue'
import { toApiError } from '@/services/http'

export function useSubmit() {
  const submitting = ref(false)
  const errorMessage = ref('')
  const errorCode = ref<string>()
  const errorTraceId = ref<string>()

  async function submit<T>(action: () => Promise<T>): Promise<T | undefined> {
    if (submitting.value) return undefined
    submitting.value = true
    errorMessage.value = ''
    errorCode.value = undefined
    errorTraceId.value = undefined
    try {
      return await action()
    } catch (error) {
      const apiError = toApiError(error)
      errorMessage.value = apiError.message
      errorCode.value = apiError.code
      errorTraceId.value = apiError.traceId
      return undefined
    } finally {
      submitting.value = false
    }
  }

  return { submitting, errorMessage, errorCode, errorTraceId, submit }
}
