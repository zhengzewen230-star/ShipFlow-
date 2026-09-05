import { readFileSync } from 'node:fs'
import { describe, expect, it } from 'vitest'

const read = (name: string) => readFileSync(new URL(name, import.meta.url), 'utf8')
const login = read('./LoginView.vue')
const styles = read('../styles/main.css')
const rightFormStyles = styles.slice(styles.indexOf('/* Right-side ERP login form:'))

describe('right-side ERP login form presentation', () => {
  it('keeps the authentication contract unchanged', () => {
    expect(login).toContain("await auth.login({ username: form.username.trim(), password: form.password, tenantCode: form.tenantCode.trim() || undefined })")
    expect(login).toContain('isSafeConsoleRedirect(route.query.redirect)')
    expect(login).toContain('<ActionError v-if="errorMessage"')
    expect(login).toContain('<form class="login-form" novalidate @submit.prevent="submit">')
    expect(login).toContain("toApiError(error, '登录失败，请检查账号信息或稍后重试。')")
    expect(login).toContain(':disabled="auth.loading"')
  })

  it('uses an aligned 440px form and enterprise field sizing', () => {
    expect(rightFormStyles).toContain('width: 440px; max-width: 100%')
    expect(rightFormStyles).toContain('min-height: 46px')
    expect(rightFormStyles).toContain('padding: 11px 14px')
    expect(rightFormStyles).toContain('gap: 20px')
    expect(rightFormStyles).toContain('width: calc(100vw - 48px)')
    expect(rightFormStyles).toContain('font-size: 14px')
    expect(rightFormStyles).toContain('font-size: 15px')
  })

  it('keeps visible focus, validation, autofill and processing states scoped to the right form', () => {
    expect(rightFormStyles).toContain('input:focus')
    expect(rightFormStyles).toContain('input[aria-invalid="true"]')
    expect(rightFormStyles).toContain('input:-webkit-autofill')
    expect(rightFormStyles).toContain('.login-submit:disabled')
    expect(rightFormStyles).not.toContain('.login-story')
  })
})
