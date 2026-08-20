import { AxiosError, AxiosHeaders, type AxiosAdapter, type AxiosResponse, type InternalAxiosRequestConfig } from 'axios'
import { afterEach, describe, expect, it, vi } from 'vitest'
import { http, notifySessionExpired, registerSessionExpiryHandler, registerSessionRefresher, setAccessToken, toApiError } from './http'

function response(config: InternalAxiosRequestConfig, status: number, data: unknown): AxiosResponse {
  return { data, status, statusText: status === 200 ? 'OK' : 'Unauthorized', headers: new AxiosHeaders(), config }
}

function unauthorized(config: InternalAxiosRequestConfig) {
  return new AxiosError('expired', 'ERR_BAD_REQUEST', config, undefined, response(config, 401, {
    success: false,
    error: { code: 'COMMON-1002', message: 'Authentication required' },
  }))
}

afterEach(() => {
  http.defaults.adapter = undefined
  registerSessionRefresher(() => Promise.resolve(undefined))
  registerSessionExpiryHandler(() => undefined)
  setAccessToken()
})

describe('HTTP error classification', () => {
  it('notifies the app once refresh cannot restore the session', () => {
    const handler = vi.fn()
    registerSessionExpiryHandler(handler)
    notifySessionExpired()
    expect(handler).toHaveBeenCalledTimes(1)
  })
  it('identifies an unmapped warehouse overview endpoint', () => {
    const error = toApiError({
      isAxiosError: true,
      config: { url: '/warehouse/overview' },
      response: { status: 404, data: { success: false } },
    } as never)

    expect(error.status).toBe(404)
    expect(error.message).toContain('接口尚未实现或服务版本不一致')
  })

  it('keeps resource not-found wording for an order detail endpoint', () => {
    const error = toApiError({
      isAxiosError: true,
      config: { url: '/orders/31' },
      response: { status: 404, data: { success: false } },
    } as never)

    expect(error.status).toBe(404)
    expect(error.message).toContain('订单不存在')
  })

  it('keeps resource not-found wording for a tracking endpoint with a business error code', () => {
    const error = toApiError({
      isAxiosError: true,
      config: { url: '/orders/31/tracking-events' },
      response: { status: 404, data: { success: false, error: { code: 'COMMON-1006' } } },
    } as never)

    expect(error.status).toBe(404)
    expect(error.message).toContain('订单不存在')
  })

  it('preserves Chinese backend reasons for measurement conflicts and SF configuration failures', () => {
    const conflict = toApiError({
      isAxiosError: true,
      config: { url: '/orders/31/measurements' },
      response: { status: 409, data: { success: false, error: { code: 'COMMON-1005', message: '订单已被其他操作更新，请刷新后重试' } } },
    } as never)
    const configuration = toApiError({
      isAxiosError: true,
      config: { url: '/orders/31/sf-international/CREATE_ORDER' },
      response: { status: 422, data: { success: false, error: { code: 'SF-1002', message: '顺丰沙箱凭据未完成配置' } } },
    } as never)

    expect(conflict.status).toBe(409)
    expect(conflict.message).toBe('订单已被其他操作更新，请刷新后重试')
    expect(configuration.status).toBe(422)
    expect(configuration.message).toBe('顺丰沙箱凭据未完成配置')
  })

  it.each([
    [401, '登录状态已失效，请重新登录。'],
    [403, '当前账号暂无访问权限，请联系管理员。'],
    [404, '订单不存在，或当前租户无权访问。'],
    [409, '请求失败，请稍后重试。'],
    [422, '请求失败，请稍后重试。'],
    [500, '服务暂时不可用，请稍后重试。'],
  ])('maps HTTP %s without silently swallowing the failure', (status, message) => {
    const error = toApiError({
      isAxiosError: true,
      config: { url: '/orders/31' },
      response: { status, data: { success: false, traceId: 'trace-http-1' } },
    } as never)

    expect(error.status).toBe(status)
    expect(error.traceId).toBe('trace-http-1')
    expect(error.message).toBe(message)
  })
})

describe('HTTP session refresh', () => {
  it('refreshes once and retries the original request with the new token', async () => {
    const refresh = vi.fn(async () => { setAccessToken('new-token'); return 'new-token' })
    let adapterCallCount = 0
    const adapter = vi.fn<AxiosAdapter>(async config => {
      adapterCallCount += 1
      return adapterCallCount === 1
        ? Promise.reject(unauthorized(config))
        : response(config, 200, { success: true, data: { ok: true } })
    })
    registerSessionRefresher(refresh)
    http.defaults.adapter = adapter

    const result = await http.get('/orders/31')

    expect(result.status).toBe(200)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(adapter).toHaveBeenCalledTimes(2)
    expect((adapter.mock.calls[1][0].headers as { get?: (name: string) => string | undefined }).get?.('Authorization')).toBe('Bearer new-token')
  })

  it('shares one refresh promise across concurrent requests', async () => {
    let releaseRefresh!: (token: string) => void
    const refreshGate = new Promise<string>(resolve => { releaseRefresh = resolve })
    const refresh = vi.fn(() => refreshGate)
    let adapterCallCount = 0
    const adapter = vi.fn<AxiosAdapter>(async config => {
      adapterCallCount += 1
      return adapterCallCount <= 2
        ? Promise.reject(unauthorized(config))
        : response(config, 200, { success: true, data: { ok: true } })
    })
    registerSessionRefresher(refresh)
    http.defaults.adapter = adapter

    const requests = [http.get('/orders/31'), http.get('/orders/32')]
    while (refresh.mock.calls.length === 0) await Promise.resolve()
    expect(refresh).toHaveBeenCalledTimes(1)
    releaseRefresh('new-token')

    await Promise.all(requests)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(adapter).toHaveBeenCalledTimes(4)
  })

  it('cleans up and notifies once when refresh returns 401', async () => {
    const expired = vi.fn()
    const refresh = vi.fn(async () => undefined)
    const adapter = vi.fn<AxiosAdapter>(async config => Promise.reject(unauthorized(config)))
    registerSessionRefresher(refresh)
    registerSessionExpiryHandler(expired)
    http.defaults.adapter = adapter

    const results = await Promise.allSettled([http.get('/orders/31'), http.get('/orders/32')])

    expect(results.every(result => result.status === 'rejected')).toBe(true)
    expect(refresh).toHaveBeenCalledTimes(1)
    expect(expired).toHaveBeenCalledTimes(1)
    expect(adapter).toHaveBeenCalledTimes(2)
  })

  it('does not refresh the refresh endpoint itself', async () => {
    const refresh = vi.fn(async () => 'new-token')
    const adapter = vi.fn<AxiosAdapter>(async config => Promise.reject(unauthorized(config)))
    registerSessionRefresher(refresh)
    http.defaults.adapter = adapter

    await expect(http.get('/auth/refresh')).rejects.toMatchObject({ status: 401 })

    expect(refresh).not.toHaveBeenCalled()
    expect(adapter).toHaveBeenCalledTimes(1)
  })
})
