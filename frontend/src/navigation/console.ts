import type { UserScope } from './access'

export type ConsoleIcon = 'overview' | 'tenant' | 'users' | 'quote' | 'store' | 'rbac' | 'logistics' | 'order' | 'warehouse' | 'tracking' | 'exception' | 'billing' | 'audit'

export interface ConsoleNavigationItem {
  label: string
  path: string
  icon: ConsoleIcon
  scope?: UserScope
  permission?: string
  anyPermissions?: string[]
  group: 'workspace' | 'master-data' | 'fulfillment' | 'settlement' | 'governance'
}

export const consoleNavigationItems: readonly ConsoleNavigationItem[] = [
  { label: '概览', path: '/app', icon: 'overview', group: 'workspace' },
  { label: '租户管理', path: '/app/tenants', icon: 'tenant', scope: 'PLATFORM', permission: 'tenant:read', group: 'governance' },
  { label: '商户入驻申请', path: '/app/onboarding-applications', icon: 'users', scope: 'PLATFORM', permission: 'tenant:manage', group: 'governance' },
  { label: '访客预估线索', path: '/app/guest-estimate-leads', icon: 'quote', scope: 'PLATFORM', permission: 'tenant:manage', group: 'governance' },
  { label: '用户管理', path: '/app/users', icon: 'users', scope: 'TENANT', permission: 'user:manage', group: 'governance' },
  { label: '店铺管理', path: '/app/stores', icon: 'store', scope: 'TENANT', permission: 'store:read', group: 'master-data' },
  { label: '角色与权限', path: '/app/rbac', icon: 'rbac', scope: 'TENANT', permission: 'role:read', group: 'governance' },
  { label: '物流基础资料', path: '/app/logistics', icon: 'logistics', permission: 'logistics:read', group: 'master-data' },
  { label: '报价', path: '/app/quotes', icon: 'quote', scope: 'TENANT', permission: 'quote:read', group: 'fulfillment' },
  { label: '订单', path: '/app/orders', icon: 'order', scope: 'TENANT', permission: 'order:read', group: 'fulfillment' },
  { label: '仓库', path: '/app/warehouse', icon: 'warehouse', scope: 'TENANT', permission: 'warehouse:manage', group: 'fulfillment' },
  { label: '轨迹', path: '/app/tracking', icon: 'tracking', scope: 'TENANT', permission: 'tracking:read', group: 'fulfillment' },
  { label: '异常与索赔', path: '/app/exceptions', icon: 'exception', scope: 'TENANT', permission: 'exception:read', group: 'fulfillment' },
  { label: '账单与对账', path: '/app/billing', icon: 'billing', scope: 'TENANT', anyPermissions: ['billing:read', 'finance:bill-import', 'finance:reconcile'], group: 'settlement' },
  { label: '审计日志', path: '/app/audit', icon: 'audit', permission: 'audit:read', group: 'governance' },
]

export const consoleGroupLabels: Record<ConsoleNavigationItem['group'], string> = {
  workspace: '工作台',
  'master-data': '基础资料',
  fulfillment: '履约中心',
  settlement: '费用结算',
  governance: '组织与治理',
}

const roleLabels: Record<string, string> = {
  PLATFORM_ADMIN: '平台超级管理员',
  MERCHANT_ADMIN: '租户管理员',
  MERCHANT_OPERATOR: '商户业务人员',
  FINANCE_OPERATOR: '财务人员',
  WAREHOUSE_OPERATOR: '仓库操作员',
  CUSTOMER_SERVICE_OPERATOR: '客服/异常专员',
}

export function visibleConsoleItems(scope: UserScope | undefined, permissions: Iterable<string>): ConsoleNavigationItem[] {
  const granted = permissions instanceof Set ? permissions : new Set(permissions)
  return consoleNavigationItems.filter(item => (!item.scope || item.scope === scope)
    && (!item.permission || granted.has(item.permission))
    && (!item.anyPermissions || item.anyPermissions.some(permission => granted.has(permission))))
}

export function roleSummary(roles: Iterable<string>, scope?: UserScope): string {
  const labels = Array.from(roles, role => roleLabels[role] ?? role)
  return labels.length ? labels.join(' / ') : scope === 'PLATFORM' ? '平台账号' : '租户账号'
}
