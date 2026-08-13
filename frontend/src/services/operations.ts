import { http, unwrap } from './http'
export interface OperationsSummary { draft?: number; pendingInbound?: number; inbound?: number; pendingPriceConfirmation?: number; readyForOutbound?: number; outbound?: number; inTransit?: number; delivered?: number; exception?: number; cancelled?: number }
export interface OperationsTodos { pendingPriceConfirmation?: number; pendingReconciliation?: number; activeExceptions?: number; submittedClaims?: number }
export const getOperationsSummary = async () => unwrap<OperationsSummary>(await http.get('/operations/summary'))
export const getOperationsTodos = async () => unwrap<OperationsTodos>(await http.get('/operations/todos'))
