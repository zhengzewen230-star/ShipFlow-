import { describe, expect, it } from 'vitest'
import { roleSummary, visibleConsoleItems } from './console'

const paths = (scope: 'PLATFORM' | 'TENANT', permissions: string[]) =>
  visibleConsoleItems(scope, permissions).map(item => item.path)

describe('console navigation permissions', () => {
  it('shows platform management only to platform permissions', () => {
    expect(paths('PLATFORM', ['tenant:read', 'tenant:manage', 'logistics:read', 'audit:read'])).toEqual([
      '/app', '/app/tenants', '/app/onboarding-applications', '/app/guest-estimate-leads', '/app/logistics', '/app/audit',
    ])
  })

  it('shows tenant administrator management and tenant business entries', () => {
    expect(paths('TENANT', ['user:manage', 'store:read', 'role:read', 'logistics:read', 'quote:read', 'order:read', 'tracking:read', 'exception:read', 'billing:read', 'audit:read'])).toEqual([
      '/app', '/app/users', '/app/stores', '/app/rbac', '/app/logistics', '/app/quotes', '/app/orders', '/app/tracking', '/app/exceptions', '/app/billing', '/app/audit',
    ])
  })

  it('keeps finance, warehouse, and customer service responsibilities separate', () => {
    expect(paths('TENANT', ['billing:read', 'finance:bill-import', 'finance:reconcile'])).toEqual(['/app', '/app/billing'])
    expect(paths('TENANT', ['finance:bill-import', 'finance:reconcile'])).toEqual(['/app', '/app/billing'])
    expect(paths('TENANT', ['warehouse:manage', 'order:read', 'tracking:read'])).toEqual(['/app', '/app/orders', '/app/warehouse', '/app/tracking'])
    expect(paths('TENANT', ['order:read', 'tracking:read', 'exception:read', 'exception:manage'])).toEqual(['/app', '/app/orders', '/app/tracking', '/app/exceptions'])
  })

  it('shows tenant audit only when the current authorization includes audit:read', () => {
    expect(paths('TENANT', ['billing:read', 'finance:bill-import', 'finance:reconcile'])).not.toContain('/app/audit')
    expect(paths('TENANT', ['billing:read', 'finance:bill-import', 'finance:reconcile', 'audit:read'])).toContain('/app/audit')
  })

  it('uses role codes for identity labels, not for authorization', () => {
    expect(roleSummary(['MERCHANT_OPERATOR'], 'TENANT')).toBe('商户业务人员')
    expect(roleSummary(['FINANCE_OPERATOR'], 'TENANT')).toBe('财务人员')
  })

  it('groups tenant navigation into master data, fulfillment, settlement and governance', () => {
    const items = visibleConsoleItems('TENANT', ['store:read', 'order:read', 'billing:read', 'audit:read'])
    expect(items.map(item => [item.path, item.group])).toEqual([
      ['/app', 'workspace'], ['/app/stores', 'master-data'], ['/app/orders', 'fulfillment'],
      ['/app/billing', 'settlement'], ['/app/audit', 'governance'],
    ])
  })
})
