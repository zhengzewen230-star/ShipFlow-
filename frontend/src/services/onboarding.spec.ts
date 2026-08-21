import { afterEach, describe, expect, it, vi } from 'vitest'
import { http } from './http'
import {
  activateOnboardingAdministrator,
  approveOnboardingApplication,
  getOnboardingApplication,
  getGuestEstimateLead,
  listGuestEstimateLeads,
  rejectOnboardingApplication,
  updateGuestEstimateLeadStatus,
} from './onboarding'

describe('onboarding service contracts', () => {
  afterEach(() => vi.restoreAllMocks())

  it('activates an administrator through the public activation endpoint', async () => {
    const response = { tenantCode: 'tenant-test', username: 'admin-test', status: 'ACTIVE' as const }
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { data: response } } as never)

    await expect(activateOnboardingAdministrator({ invitationToken: 'opaque-token', password: 'long-test-password' })).resolves.toEqual(response)
    expect(post).toHaveBeenCalledWith('/public/onboarding-activations', { invitationToken: 'opaque-token', password: 'long-test-password' }, expect.objectContaining({ headers: expect.objectContaining({ 'X-Request-ID': expect.any(String) }) }))
  })

  it('uses platform approval and rejection endpoints with the application version', async () => {
    const post = vi.spyOn(http, 'post').mockResolvedValue({ data: { data: { application: { id: 7 }, invitationToken: 'one-time-token' } } } as never)

    await approveOnboardingApplication(7, { tenantCode: 'tenant-test', adminUsername: 'admin-test', reviewRemark: 'approved for test', version: 2 })
    await rejectOnboardingApplication(8, { reviewRemark: 'needs more information', version: 3 })

    expect(post).toHaveBeenNthCalledWith(1, '/platform/onboarding-applications/7/approve', expect.objectContaining({ version: 2 }), expect.objectContaining({ headers: expect.objectContaining({ 'X-Request-ID': expect.any(String), 'Idempotency-Key': expect.any(String) }) }))
    expect(post).toHaveBeenNthCalledWith(2, '/platform/onboarding-applications/8/reject', expect.objectContaining({ version: 3 }), expect.objectContaining({ headers: expect.objectContaining({ 'X-Request-ID': expect.any(String) }) }))
  })

  it('loads an onboarding application detail through the platform endpoint', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { data: { id: 7, applicationNo: 'APP-TEST' } } } as never)

    await expect(getOnboardingApplication(7)).resolves.toMatchObject({ id: 7, applicationNo: 'APP-TEST' })
    expect(get).toHaveBeenCalledWith('/platform/onboarding-applications/7')
  })

  it('keeps platform lead list, detail, and status paths separate', async () => {
    const get = vi.spyOn(http, 'get').mockResolvedValue({ data: { data: { items: [], page: 1, pageSize: 20, total: 0, totalPages: 0 } } } as never)
    const patch = vi.spyOn(http, 'patch').mockResolvedValue({ data: { data: { id: 9, status: 'CONTACTING', version: 2 } } } as never)

    await listGuestEstimateLeads({ page: 1, pageSize: 20, status: 'RECEIVED' })
    await getGuestEstimateLead(9)
    await updateGuestEstimateLeadStatus(9, { status: 'CONTACTING', handlingRemark: 'first contact', version: 1 })

    expect(get).toHaveBeenNthCalledWith(1, '/platform/guest-estimate-leads', { params: { page: 1, pageSize: 20, status: 'RECEIVED' } })
    expect(get).toHaveBeenNthCalledWith(2, '/platform/guest-estimate-leads/9')
    expect(patch).toHaveBeenCalledWith('/platform/guest-estimate-leads/9/status', expect.objectContaining({ status: 'CONTACTING', version: 1 }), expect.objectContaining({ headers: expect.objectContaining({ 'X-Request-ID': expect.any(String), 'Idempotency-Key': expect.any(String) }) }))
  })
})
