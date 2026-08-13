import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authService from '@/services/auth'
import { clearCsrfState } from '@/services/csrf'
import { registerSessionRefresher, setAccessToken } from '@/services/http'
export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string>(); const expiresAt = ref<number>(); const currentUser = ref<authService.CurrentUser>(); const loading = ref(false); const initialized = ref(false)
  let restorePromise: Promise<boolean> | undefined
  const isAuthenticated = computed(() => Boolean(accessToken.value && expiresAt.value && expiresAt.value > Date.now()))
  const permissions = computed(() => new Set(currentUser.value?.permissions ?? [])); const scope = computed(() => currentUser.value?.scope)
  function applyToken(result: authService.TokenResponse) { accessToken.value = result.accessToken; expiresAt.value = Date.now() + result.expiresIn * 1000; setAccessToken(result.accessToken) }
  function clearSession() { accessToken.value = undefined; expiresAt.value = undefined; currentUser.value = undefined; setAccessToken() }
  async function refresh(): Promise<string | undefined> { try { const result = await authService.refreshToken(); applyToken(result); return result.accessToken } catch { clearSession(); return undefined } }
  registerSessionRefresher(refresh)
  async function loadCurrentUser() { currentUser.value = await authService.getCurrentUser(); return currentUser.value }
  async function login(payload: authService.LoginPayload) { loading.value = true; try { const result = await authService.login(payload); applyToken(result); await loadCurrentUser(); initialized.value = true; return result } finally { loading.value = false } }
  async function restoreSession() { if (initialized.value) return isAuthenticated.value; restorePromise ??= (async () => { const token = await refresh(); if (token) await loadCurrentUser(); initialized.value = true; return Boolean(token) })().catch(() => { clearSession(); initialized.value = true; return false }).finally(() => { restorePromise = undefined }); return restorePromise }
  async function logout() { loading.value = true; try { await authService.logout() } finally { clearSession(); clearCsrfState(); initialized.value = true; loading.value = false } }
  function hasPermission(permission?: string) { return !permission || permissions.value.has(permission) }
  function hasScope(required?: 'PLATFORM' | 'TENANT') { return !required || scope.value === required }
  return { accessToken, expiresAt, currentUser, loading, initialized, isAuthenticated, permissions, scope, login, refresh, restoreSession, loadCurrentUser, logout, logoutLocal: clearSession, clearSession, hasPermission, hasScope }
})
