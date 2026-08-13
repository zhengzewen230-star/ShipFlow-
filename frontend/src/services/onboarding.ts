import { http, toApiError, unwrap } from './http'
import { writeConfig } from './request'

export type TransportMode = 'OCEAN' | 'AIR' | 'ROAD' | 'RAIL' | 'COURIER'
export type GuestCargoType = 'GENERAL' | 'BATTERY' | 'SENSITIVE' | 'LIQUID_POWDER' | 'FRAGILE' | 'OVERSIZED' | 'OTHER'
export interface GuestEstimateRequest { originCountry: string; destinationCountry: string; transportMode: TransportMode; cargoType: GuestCargoType; cargoName: string; weight: number; volume: number; contactName: string; businessEmail: string; contactPhone: string }
export interface GuestEstimateResponse { referenceNo: string; status: 'RECEIVED'; formalQuote: false; notice: string; submittedAt: string }
export interface OnboardingApplication { id: number; applicationNo: string; companyName: string; contactName: string; businessEmail: string; contactPhone: string; countryCode: string; status: 'PENDING' | 'APPROVED' | 'REJECTED'; reviewRemark?: string; tenantId?: number; initialUserId?: number; version: number; createdAt?: string; reviewedAt?: string }
export interface CreateOnboardingApplicationRequest { companyName: string; contactName: string; businessEmail: string; contactPhone: string; countryCode: string }
export function guestEstimateErrorMessage(error: unknown) {
  const apiError = toApiError(error)
  const friendly: Record<string, string> = {
    'COMMON-1001': '请检查必填信息、国家/地区和货物名称后重新提交。',
    'ONBOARDING-1005': '起运国家与目的国家不能相同。',
    'COMMON-1009': '本次提交信息与已处理请求不一致，请刷新后重试。',
  }
  if (friendly[apiError.code ?? '']) apiError.message = friendly[apiError.code ?? '']
  if (!apiError.status || apiError.status >= 500) {
    apiError.message = apiError.traceId
      ? `服务暂时不可用，请稍后重试。（追踪编号：${apiError.traceId}）`
      : '服务暂时不可用，请稍后重试。'
  }
  return apiError
}
export async function submitGuestEstimate(payload: GuestEstimateRequest) {
  try { return unwrap<GuestEstimateResponse>(await http.post('/public/estimate-requests', payload, writeConfig('guest-estimate'))) }
  catch (error) { throw guestEstimateErrorMessage(error) }
}
export async function submitOnboardingApplication(payload: CreateOnboardingApplicationRequest) { return unwrap<OnboardingApplication>(await http.post('/public/onboarding-applications', payload, writeConfig('onboarding-application'))) }
