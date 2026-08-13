import type { AxiosRequestConfig } from 'axios'
import { createIdempotencyKey } from './http'

export function writeConfig(prefix: string, idempotent = true): AxiosRequestConfig {
  const headers: Record<string, string> = { 'X-Request-ID': createIdempotencyKey('request') }
  if (idempotent) headers['Idempotency-Key'] = createIdempotencyKey(prefix)
  return { headers }
}
