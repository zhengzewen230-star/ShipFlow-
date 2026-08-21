import { describe, expect, it } from 'vitest'
import { defaultConsolePath, isPublicRouteName, isSafeConsoleRedirect, publicRouteRedirect, quoteConsolePath } from './access'

describe('identity navigation', () => {
  it('keeps visitor quotation public until authentication exists', () => {
    expect(isPublicRouteName('quote')).toBe(true)
    expect(publicRouteRedirect({ isAuthenticated: false }, 'quote')).toBeUndefined()
  })

  it('sends tenant users to the console quotation flow', () => {
    expect(quoteConsolePath({ isAuthenticated: true, scope: 'TENANT', permissions: ['quote:create'] })).toBe('/app/quotes/create')
    expect(defaultConsolePath({ isAuthenticated: true, scope: 'TENANT' })).toBe('/app')
    expect(publicRouteRedirect({ isAuthenticated: true, scope: 'TENANT', permissions: ['quote:create'] }, 'quote')).toBe('/app/quotes/create')
    expect(publicRouteRedirect({ isAuthenticated: true, scope: 'TENANT', permissions: ['billing:read'] }, 'quote')).toBe('/app')
  })

  it('keeps platform users inside the platform console', () => {
    expect(quoteConsolePath({ isAuthenticated: true, scope: 'PLATFORM' })).toBe('/app')
    expect(defaultConsolePath({ isAuthenticated: true, scope: 'PLATFORM' })).toBe('/app')
    expect(publicRouteRedirect({ isAuthenticated: true, scope: 'PLATFORM' }, 'home')).toBe('/app')
  })

  it('only accepts internal console redirects after login', () => {
    expect(isSafeConsoleRedirect('/app/quotes/create')).toBe(true)
    expect(isSafeConsoleRedirect('/quote')).toBe(false)
    expect(isSafeConsoleRedirect('https://example.test/app')).toBe(false)
  })
})
