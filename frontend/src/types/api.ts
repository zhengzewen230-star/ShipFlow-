export type Id = string
export interface ApiSuccess<T> { success: true; traceId: string; message?: string; data: T }
export interface ApiErrorBody { success: false; traceId?: string; error?: { code?: string; message?: string; details?: Record<string, unknown> } }
export interface PageMeta { page: number; pageSize: number; total: number; totalPages: number }
export interface PageQuery { page?: number; pageSize?: number }
export interface ApiPage<T> extends PageMeta { items: T[] }
export type JsonObject = Record<string, unknown>
export class ApiError extends Error {
  constructor(message: string, public readonly status?: number, public readonly code?: string, public readonly traceId?: string, public readonly details?: Record<string, unknown>) { super(message); this.name = 'ApiError' }
}
