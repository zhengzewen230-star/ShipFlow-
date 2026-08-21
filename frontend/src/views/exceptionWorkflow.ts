import type { ExceptionStatus } from '@/services/exceptions'

const transitions: Record<ExceptionStatus, ExceptionStatus[]> = {
  OPEN: ['PROCESSING'],
  PROCESSING: ['WAITING_PROVIDER_FEEDBACK', 'PENDING_FINANCE_CONFIRMATION'],
  WAITING_PROVIDER_FEEDBACK: ['PROCESSING'],
  PENDING_FINANCE_CONFIRMATION: ['RESOLVED'],
  RESOLVED: ['CLOSED'],
  CLOSED: [],
}

export function allowedExceptionTransitions(status: ExceptionStatus) { return transitions[status] }
export function canMutateException(status: ExceptionStatus) { return status !== 'CLOSED' }
export function idList(value: string) { return [...new Set(value.split(',').map(item => item.trim()).filter(Boolean))] }
