import axios, { AxiosHeaders, type AxiosError, type InternalAxiosRequestConfig } from 'axios'
import { ensureCsrfToken } from './csrf'
import { ApiError, type ApiErrorBody, type ApiSuccess } from '@/types/api'
export { type ApiSuccess } from '@/types/api'
export const http = axios.create({ baseURL: '/api/v1', withCredentials: true, timeout: 15_000, headers: { Accept: 'application/json' } })
let accessToken: string | undefined
let refreshSession: (() => Promise<string | undefined>) | undefined
let refreshPromise: Promise<string | undefined> | undefined
const unsafeMethods = new Set(['post', 'put', 'patch', 'delete'])
const noRefreshPaths = ['/auth/login', '/auth/refresh', '/auth/logout', '/auth/csrf']
export function setAccessToken(token?: string) { accessToken = token }
export function registerSessionRefresher(refresher: () => Promise<string | undefined>) { refreshSession = refresher }
http.interceptors.request.use(async config => { const headers = AxiosHeaders.from(config.headers); if (accessToken) headers.set('Authorization', `Bearer ${accessToken}`); if (unsafeMethods.has((config.method ?? 'get').toLowerCase())) headers.set('X-XSRF-TOKEN', await ensureCsrfToken()); config.headers = headers; return config })
http.interceptors.response.use(response => response, async (error: AxiosError<ApiErrorBody>) => {
  const config = error.config as (InternalAxiosRequestConfig & { _sessionRetried?: boolean }) | undefined
  const path = config?.url ?? ''
  if (error.response?.status === 401 && config && !config._sessionRetried && !noRefreshPaths.some(item => path.includes(item)) && refreshSession) { config._sessionRetried = true; refreshPromise ??= refreshSession().finally(() => { refreshPromise = undefined }); const token = await refreshPromise; if (token) return http(config) }
  throw toApiError(error)
})
export function unwrap<T>(response: { data: ApiSuccess<T> }): T { return response.data.data }
export function toApiError(error: unknown, fallback = '请求失败，请稍后重试。'): ApiError {
  if (error instanceof ApiError) return error
  if (axios.isAxiosError<ApiErrorBody>(error)) { const status = error.response?.status; const body = error.response?.data; const message = status === 401 ? '登录状态已失效，请重新登录。' : status === 403 ? '你没有执行此操作的权限。' : status === 404 ? '请求的内容不存在或已被移除。' : status ? fallback : '网络连接失败，请检查网络后重试。'; return new ApiError(body?.error?.message || message, status, body?.error?.code, body?.traceId, body?.error?.details) }
  return new ApiError(error instanceof Error ? error.message : fallback)
}
export function getApiErrorMessage(error: unknown, fallback: string) { return toApiError(error, fallback).message }
export function createIdempotencyKey(prefix = 'sf') { const random = typeof crypto !== 'undefined' && 'randomUUID' in crypto ? crypto.randomUUID() : `${Date.now()}-${Math.random().toString(16).slice(2)}`; return `${prefix}-${random}` }
