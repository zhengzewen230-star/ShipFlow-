import axios, { AxiosHeaders, type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ensureCsrfToken } from './csrf'
import { ApiError, type ApiErrorBody, type ApiSuccess } from '@/types/api'

export { type ApiSuccess } from '@/types/api'
export const http = axios.create({ baseURL: '/api/v1', withCredentials: true, timeout: 15_000, headers: { Accept: 'application/json' } })
let accessToken: string | undefined
let refreshSession: (() => Promise<string | undefined>) | undefined
let sessionExpiryHandler: (() => void) | undefined
let refreshPromise: Promise<string | undefined> | undefined
let sessionExpiryNotified = false
const unsafeMethods = new Set(['post', 'put', 'patch', 'delete'])
const noRefreshPaths = ['/auth/login', '/auth/refresh', '/auth/logout', '/auth/csrf']

export function setAccessToken(token?: string) {
  accessToken = token
  if (token) sessionExpiryNotified = false
}
export function registerSessionRefresher(refresher: () => Promise<string | undefined>) { refreshSession = refresher }
export function registerSessionExpiryHandler(handler: () => void) {
  sessionExpiryHandler = handler
  sessionExpiryNotified = false
}
export function notifySessionExpired() { sessionExpiryHandler?.() }

function notifySessionExpiredOnce() {
  if (sessionExpiryNotified) return
  sessionExpiryNotified = true
  notifySessionExpired()
}

function refreshAccessTokenOnce() {
  if (!refreshPromise) {
    refreshPromise = Promise.resolve()
      .then(() => refreshSession?.())
      .catch(() => undefined)
      .finally(() => { refreshPromise = undefined })
  }
  return refreshPromise
}

http.interceptors.request.use(async config => {
  const headers = AxiosHeaders.from(config.headers)
  if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`)
  if (unsafeMethods.has((config.method ?? 'get').toLowerCase())) headers.set('X-XSRF-TOKEN', await ensureCsrfToken())
  config.headers = headers
  return config
})

http.interceptors.response.use(response => response, async (error: AxiosError<ApiErrorBody>) => {
  const config = error.config as (InternalAxiosRequestConfig & { _sessionRetried?: boolean }) | undefined
  const path = config?.url ?? ''
  if (error.response?.status === 401 && config && !config._sessionRetried && !noRefreshPaths.some(item => path.includes(item)) && refreshSession) {
    config._sessionRetried = true
    const token = await refreshAccessTokenOnce()
    if (token) return http(config)
    notifySessionExpiredOnce()
  }
  throw toApiError(error)
})

export function unwrap<T>(response: { data: ApiSuccess<T> }): T { return response.data.data }

export function toApiError(error: unknown, fallback = '请求失败，请稍后重试。'): ApiError {
  if (error instanceof ApiError) return error
  if (axios.isAxiosError<ApiErrorBody>(error)) {
    const status = error.response?.status
    const requestPath = error.config?.url ?? ''
    const body = error.response?.data
    const code = body?.error?.code
    const messages: Record<string, string> = {
      'WAREHOUSE-1001': '订单当前不在待入库状态，无法执行入库。',
      'WAREHOUSE-1002': '订单当前不允许复称，或复称数据不合法。',
      'WAREHOUSE-1003': '订单缺少可用的物流渠道，暂时无法出库。',
      'WAREHOUSE-1004': '订单当前不在待出库状态，无法执行出库。',
      'WAREHOUSE-1005': '该订单已经有出库记录，请勿重复操作。',
      'COMMON-1005': '订单已被其他操作更新，请刷新后重试。',
      'COMMON-1006': '订单不存在，或当前租户无权访问。',
      'COMMON-1009': '同一个幂等键不能用于不同的仓库操作。',
      'COMMON-1010': '该仓库操作正在处理中，请稍后重试。',
      'BILL-1001': '账单文件或导入参数不合法。',
      'BILL-1002': '账单导入批次发生并发冲突，请稍后重试。',
      'BILL-1003': '账单明细重复。',
      'RECON-1003': '对账状态或版本冲突，请刷新后重试。',
      'ORDER-1010': '订单当前不在费用确认流程中，或费用调整已处理。',
      'ORDER-1011': '提交的费用与服务端费用调整不一致。',
      'SF-1001': '顺丰沙箱未启用或地址不在允许的白名单内。',
      'SF-1002': '顺丰沙箱凭据尚未完整配置。',
      'SF-1003': '顺丰接口超时或重试配置无效。',
      'SF-1004': '顺丰官方签名算法尚未配置，暂不能联调。',
      'SF-1005': '顺丰服务暂时不可用，请稍后重试。',
      'SF-1006': '顺丰请求被中断，请稍后重试。',
      'SF-1007': '顺丰服务返回不可用状态，请稍后重试。',
      'SF-1008': '顺丰订单尚未创建，不能执行当前操作。',
      'SF-1009': '已出库或已取消的顺丰订单不能重复取消。',
      'SF-1010': '当前顺丰订单状态不允许继续操作。',
      'SF-1011': '顺丰订单状态存储尚未完成迁移。',
    }
    const missingEndpoint = status === 404
      && !code
      && (requestPath.includes('/warehouse/overview') || requestPath.includes('/tracking-events'))
    if (missingEndpoint) return new ApiError('接口尚未实现或服务版本不一致，请联系管理员确认后端版本。', status, code, body?.traceId, body?.error?.details)
    const backendMessage = body?.error?.message?.trim()
    const detailsMessage = body?.error?.details
      ? Object.entries(body.error.details).map(([field, value]) => `${field}: ${String(value)}`).join('；')
      : ''
    const message = backendMessage
      || (code && messages[code])
      || (status === 401 ? '登录状态已失效，请重新登录。'
        : status === 403 ? '当前账号暂无访问权限，请联系管理员。'
              : status === 404 ? (requestPath.includes('/orders') ? '订单不存在，或当前租户无权访问。' : '资源不存在，或当前租户无权访问。')
            : status && status >= 500 ? '服务暂时不可用，请稍后重试。'
              : status ? fallback : '网络连接失败，请检查网络后重试。')
    return new ApiError(detailsMessage && backendMessage === '请求参数错误'
      ? `${message}（${detailsMessage}）`
      : message, status, code, body?.traceId, body?.error?.details)
  }
  return new ApiError(error instanceof Error ? error.message : fallback)
}

export function getApiErrorMessage(error: unknown, fallback: string) { return toApiError(error, fallback).message }
export function createIdempotencyKey(prefix = 'sf') {
  const random = typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`
  return `${prefix}-${random}`
}
