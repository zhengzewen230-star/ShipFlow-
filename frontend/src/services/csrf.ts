import axios from 'axios'
const csrfHttp = axios.create({ baseURL: '/api/v1', withCredentials: true, timeout: 15_000 })
let csrfPromise: Promise<string> | undefined
function readCookie(name: string): string | undefined {
  if (typeof document === 'undefined') return undefined
  const prefix = `${encodeURIComponent(name)}=`
  const value = document.cookie.split('; ').find(item => item.startsWith(prefix))?.slice(prefix.length)
  return value ? decodeURIComponent(value) : undefined
}
export function getCsrfToken() { return readCookie('XSRF-TOKEN') }
export async function ensureCsrfToken(force = false): Promise<string> {
  const existing = !force && getCsrfToken(); if (existing) return existing
  csrfPromise ??= csrfHttp.get('/auth/csrf').then(() => { const token = getCsrfToken(); if (!token) throw new Error('未能建立安全请求会话，请刷新页面后重试。'); return token }).finally(() => { csrfPromise = undefined })
  return csrfPromise
}
export function clearCsrfState() { csrfPromise = undefined }
