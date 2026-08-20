import { http, unwrap } from './http'
export interface LoginPayload { username: string; password: string; tenantCode?: string }
export interface TokenResponse { accessToken: string; expiresIn: number; userId?: string; tenantId?: string }
export interface CurrentUser { userId: string; username: string; displayName?: string; scope: 'PLATFORM' | 'TENANT'; tenantId?: string; roles: string[]; permissions: string[] }
export async function login(payload: LoginPayload): Promise<TokenResponse> { return unwrap(await http.post('/auth/login', payload)) }
export async function refreshToken(): Promise<TokenResponse> { return unwrap(await http.post('/auth/refresh')) }
export async function logout(): Promise<void> { await http.post('/auth/logout') }
export async function getCurrentUser(): Promise<CurrentUser> { return unwrap(await http.get('/users/me')) }
