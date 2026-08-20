import { computed, ref } from 'vue'
import { defineStore } from 'pinia'
import * as authService from '@/services/auth'
import { clearCsrfState } from '@/services/csrf'
import { registerSessionExpiryHandler, registerSessionRefresher, setAccessToken } from '@/services/http'

export const useAuthStore = defineStore('auth', () => {
  const accessToken = ref<string>()
  const expiresAt = ref<number>()
  const currentUser = ref<authService.CurrentUser>()
  const loading = ref(false)
  const initialized = ref(false)
  const initializing = ref(false)
  let restorePromise: Promise<boolean> | undefined

  const isAuthenticated = computed(() => Boolean(accessToken.value && expiresAt.value && expiresAt.value > Date.now()))
  const permissions = computed(() => new Set(currentUser.value?.permissions ?? []))
  const roles = computed(() => new Set(currentUser.value?.roles ?? []))
  const scope = computed(() => currentUser.value?.scope)
  const tenantId = computed(() => currentUser.value?.tenantId)

  function applyToken(result: authService.TokenResponse) {
    accessToken.value = result.accessToken
    expiresAt.value = Date.now() + result.expiresIn * 1000
    setAccessToken(result.accessToken)
  }

  function clearSession() {
    accessToken.value = undefined
    expiresAt.value = undefined
    currentUser.value = undefined
    setAccessToken()
  }

  async function refresh(): Promise<string | undefined> {
    try {
      const result = await authService.refreshToken()
      applyToken(result)
      return result.accessToken
    } catch {
      clearSession()
      return undefined
    }
  }

  registerSessionRefresher(refresh)
  registerSessionExpiryHandler(() => {
    if (typeof window !== 'undefined') window.dispatchEvent(new Event('shipflow:session-expired'))
  })

  async function loadCurrentUser() {
    currentUser.value = await authService.getCurrentUser()
    return currentUser.value
  }

  async function restoreSession() {
    if (initialized.value) return Boolean(isAuthenticated.value && currentUser.value)
    restorePromise ??= (async () => {
      initializing.value = true
      const token = await refresh()
      if (!token) return false
      await loadCurrentUser()
      return true
    })().catch(() => {
      clearSession()
      return false
    }).finally(() => {
      initialized.value = true
      initializing.value = false
      restorePromise = undefined
    })
    return restorePromise
  }

  async function login(payload: authService.LoginPayload) {
    loading.value = true
    initialized.value = false
    initializing.value = true
    currentUser.value = undefined
    try {
      // The HTTP interceptor obtains CSRF before this write request. Identity and permissions
      // are only considered ready after the bearer token has been exchanged for /users/me.
      const result = await authService.login(payload)
      applyToken(result)
      await loadCurrentUser()
      initialized.value = true
      return result
    } catch (error) {
      clearSession()
      initialized.value = true
      throw error
    } finally {
      initializing.value = false
      loading.value = false
    }
  }

  async function logout() {
    loading.value = true
    try {
      await authService.logout()
    } finally {
      clearSession()
      clearCsrfState()
      initialized.value = true
      initializing.value = false
      loading.value = false
    }
  }

  function hasPermission(permission?: string) { return !permission || permissions.value.has(permission) }
  function hasRole(role?: string) { return !role || roles.value.has(role) }
  function hasScope(required?: 'PLATFORM' | 'TENANT') { return !required || scope.value === required }

  return {
    accessToken, expiresAt, currentUser, loading, initialized, initializing, isAuthenticated,
    permissions, roles, scope, tenantId, login, refresh, restoreSession, loadCurrentUser, logout,
    logoutLocal: clearSession, clearSession, hasPermission, hasRole, hasScope,
  }
})
