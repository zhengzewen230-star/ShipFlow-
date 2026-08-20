export type UserScope = 'PLATFORM' | 'TENANT'

export interface NavigationIdentity {
  isAuthenticated: boolean
  scope?: UserScope
  permissions?: Iterable<string>
}

const publicRouteNames = new Set(['home', 'quote', 'apply', 'login'])

export function defaultConsolePath(_identity: NavigationIdentity): string {
  return '/app'
}

export function quoteConsolePath(identity: NavigationIdentity): string {
  const permissions = new Set(identity.permissions ?? [])
  return identity.scope === 'TENANT' && permissions.has('quote:create')
    ? '/app/quotes/create'
    : defaultConsolePath(identity)
}

export function isPublicRouteName(name: unknown): boolean {
  return typeof name === 'string' && publicRouteNames.has(name)
}

export function publicRouteRedirect(identity: NavigationIdentity, name: unknown): string | undefined {
  if (!identity.isAuthenticated || !isPublicRouteName(name)) return undefined
  return name === 'quote' ? quoteConsolePath(identity) : defaultConsolePath(identity)
}

export function isSafeConsoleRedirect(value: unknown): value is string {
  return typeof value === 'string' && (value === '/app' || value.startsWith('/app/'))
}
