import { http, toApiError, unwrap } from './http'
import { writeConfig } from './request'
import type { ApiPage, PageQuery } from '@/types/api'

export type TransportMode = 'OCEAN' | 'AIR' | 'ROAD' | 'RAIL' | 'COURIER'
export type GuestCargoType = 'GENERAL' | 'BATTERY' | 'SENSITIVE' | 'LIQUID_POWDER' | 'FRAGILE' | 'OVERSIZED' | 'OTHER'
export interface GuestEstimateRequest { originCountry: string; destinationCountry: string; transportMode: TransportMode; cargoType: GuestCargoType; cargoName: string; weight: number; volume: number; contactName: string; businessEmail: string; contactPhone: string }
export interface GuestEstimateResponse { referenceNo: string; status: 'RECEIVED'; formalQuote: false; notice: string; submittedAt: string }
export type GuestEstimateLeadStatus = 'RECEIVED' | 'CONTACTING' | 'QUALIFIED' | 'CLOSED'
export interface GuestEstimateLead { id: number; referenceNo: string; originCountry: string; destinationCountry: string; transportMode: TransportMode; cargoType: GuestCargoType; cargoName: string; weight: number; volume: number; contactName: string; businessEmail: string; contactPhone: string; status: GuestEstimateLeadStatus; handlingRemark?: string; handledByUserId?: number; handledAt?: string; version: number; createdAt: string; updatedAt: string }
export interface GuestEstimateLeadQuery extends PageQuery { status?: GuestEstimateLeadStatus; keyword?: string; from?: string; to?: string }
export interface UpdateGuestEstimateLeadStatusRequest { status: Exclude<GuestEstimateLeadStatus, 'RECEIVED'>; handlingRemark?: string; version: number }
export interface OnboardingApplication { id: number; applicationNo: string; companyName: string; contactName: string; businessEmail: string; contactPhone: string; countryCode: string; status: 'PENDING' | 'APPROVED' | 'REJECTED'; reviewRemark?: string; tenantId?: number; initialUserId?: number; version: number; createdAt?: string; reviewedAt?: string }
export interface CreateOnboardingApplicationRequest { companyName: string; contactName: string; businessEmail: string; contactPhone: string; countryCode: string }
export interface OnboardingApplicationQuery extends PageQuery { status?: OnboardingApplication['status'] }
export interface ActivationRequest { invitationToken: string; password: string }
export interface ActivationResponse { tenantCode?: string; username?: string; status?: 'ACTIVE' }
export interface ApproveOnboardingApplicationRequest { tenantCode: string; adminUsername: string; reviewRemark: string; version: number }
export interface RejectOnboardingApplicationRequest { reviewRemark: string; version: number }
export interface OnboardingApproval { application: OnboardingApplication; invitationToken?: string }
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
      ? `服务暂未完成初始化，请稍后重试。（追踪编号：${apiError.traceId}）`
      : '服务暂未完成初始化，请稍后重试。'
  }
  return apiError
}
export async function submitGuestEstimate(payload: GuestEstimateRequest) {
  try { return unwrap<GuestEstimateResponse>(await http.post('/public/estimate-requests', payload, writeConfig('guest-estimate'))) }
  catch (error) { throw guestEstimateErrorMessage(error) }
}
export async function submitOnboardingApplication(payload: CreateOnboardingApplicationRequest) { return unwrap<OnboardingApplication>(await http.post('/public/onboarding-applications', payload, writeConfig('onboarding-application'))) }
export async function activateOnboardingAdministrator(payload: ActivationRequest) { return unwrap<ActivationResponse>(await http.post('/public/onboarding-activations', payload, writeConfig('onboarding-activation', false))) }
export const listOnboardingApplications = async (params: OnboardingApplicationQuery = {}) => unwrap<ApiPage<OnboardingApplication>>(await http.get('/platform/onboarding-applications', { params }))
export const getOnboardingApplication = async (applicationId: number) => unwrap<OnboardingApplication>(await http.get(`/platform/onboarding-applications/${applicationId}`))
export const approveOnboardingApplication = async (applicationId: number, payload: ApproveOnboardingApplicationRequest) => unwrap<OnboardingApproval>(await http.post(`/platform/onboarding-applications/${applicationId}/approve`, payload, writeConfig('onboarding-approve')))
export const rejectOnboardingApplication = async (applicationId: number, payload: RejectOnboardingApplicationRequest) => unwrap<OnboardingApplication>(await http.post(`/platform/onboarding-applications/${applicationId}/reject`, payload, writeConfig('onboarding-reject', false)))
export const listGuestEstimateLeads = async (params: GuestEstimateLeadQuery = {}) => unwrap<ApiPage<GuestEstimateLead>>(await http.get('/platform/guest-estimate-leads', { params }))
export const getGuestEstimateLead = async (leadId: number) => unwrap<GuestEstimateLead>(await http.get(`/platform/guest-estimate-leads/${leadId}`))
export const updateGuestEstimateLeadStatus = async (leadId: number, payload: UpdateGuestEstimateLeadStatusRequest) => unwrap<GuestEstimateLead>(await http.patch(`/platform/guest-estimate-leads/${leadId}/status`, payload, writeConfig('guest-estimate-lead-status')))
