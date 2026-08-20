-- UTF-8, UTC. Aligns tenant responsibilities with the endpoint authorization matrix.
-- This migration changes only the permission dictionary and role-permission bindings.

INSERT INTO sys_permission (permission_code, permission_name, description)
VALUES
  ('quote:read', 'Read quotes', 'Read tenant formal quotes'),
  ('quote:create', 'Create quotes', 'Create tenant formal quotes'),
  ('quote:validate', 'Validate quotes', 'Validate tenant formal quotes'),
  ('order:read', 'Read orders', 'Read tenant shipment orders'),
  ('order:manage', 'Manage orders', 'Update, submit, or cancel tenant shipment orders'),
  ('order:price-confirm', 'Confirm order price', 'Confirm a measured order price increase'),
  ('tracking:read', 'Read tracking', 'Read tenant shipment tracking'),
  ('exception:read', 'Read exceptions', 'Read tenant exception cases and claims'),
  ('exception:manage', 'Manage exceptions', 'Create and process tenant exception cases and claims'),
  ('billing:read', 'Read billing', 'Read tenant bill batches, bill details, and reconciliations'),
  ('warehouse:manage', 'Manage warehouse', 'Perform tenant inbound, measurement, and outbound operations'),
  ('operations:read', 'Read operations dashboard', 'Read tenant operations summary and todos')
ON DUPLICATE KEY UPDATE
  permission_name = VALUES(permission_name),
  description = VALUES(description);

-- Provision all standard tenant roles for existing active tenants. Role creation
-- is platform-controlled; tenant users only receive roles explicitly assigned to them.
INSERT INTO sys_role (tenant_id, role_code, role_name, role_scope, status, deleted)
SELECT t.id, matrix.role_code, matrix.role_name, 'TENANT', 'ACTIVE', 0
FROM tenant t
CROSS JOIN (
  SELECT 'MERCHANT_OPERATOR' AS role_code, 'Merchant operator' AS role_name
  UNION ALL SELECT 'FINANCE_OPERATOR', 'Finance operator'
  UNION ALL SELECT 'WAREHOUSE_OPERATOR', 'Warehouse operator'
  UNION ALL SELECT 'TEST_NO_PERMISSION', 'No permission test role'
  UNION ALL SELECT 'CUSTOMER_SERVICE_OPERATOR', 'Customer service operator'
) matrix
WHERE t.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role r
    WHERE r.tenant_id = t.id AND r.role_code = matrix.role_code
  );

-- Customer service is a tenant browser role. It is intentionally distinct from
-- the HMAC-only MOCK_LOGISTICS_SYSTEM platform integration identity.
INSERT INTO sys_role (tenant_id, role_code, role_name, role_scope, status, deleted)
SELECT t.id, 'CUSTOMER_SERVICE_OPERATOR', 'Customer service operator', 'TENANT', 'ACTIVE', 0
FROM tenant t
WHERE t.status = 'ACTIVE'
  AND NOT EXISTS (
    SELECT 1 FROM sys_role r
    WHERE r.tenant_id = t.id AND r.role_code = 'CUSTOMER_SERVICE_OPERATOR'
  );

-- Rebuild bindings for standard tenant roles. Deleting first also removes legacy
-- broad grants such as order:operate, logistics:read for unrelated roles, and
-- any accidentally assigned platform or callback permission.
DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
WHERE r.role_scope = 'TENANT'
  AND r.role_code IN ('MERCHANT_ADMIN', 'MERCHANT_OPERATOR', 'FINANCE_OPERATOR',
                      'WAREHOUSE_OPERATOR', 'CUSTOMER_SERVICE_OPERATOR', 'TEST_NO_PERMISSION');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
  CASE r.role_code
    WHEN 'MERCHANT_ADMIN' THEN 'store:create'
    WHEN 'MERCHANT_OPERATOR' THEN 'store:read'
    WHEN 'FINANCE_OPERATOR' THEN 'billing:read'
    WHEN 'WAREHOUSE_OPERATOR' THEN 'warehouse:manage'
    WHEN 'CUSTOMER_SERVICE_OPERATOR' THEN 'order:read'
  END
)
WHERE r.role_scope = 'TENANT'
  AND r.deleted = 0
  AND r.status = 'ACTIVE'
  AND r.role_code IN ('MERCHANT_ADMIN', 'MERCHANT_OPERATOR', 'FINANCE_OPERATOR',
                      'WAREHOUSE_OPERATOR', 'CUSTOMER_SERVICE_OPERATOR');

-- Additional grants use an explicit matrix to make every role's responsibility auditable.
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON (
  (r.role_code = 'MERCHANT_ADMIN' AND p.permission_code IN (
    'store:read','store:manage','user:read','user:manage','role:read','role:manage','permission:read',
    'logistics:read','quote:read','quote:create','quote:validate','order:read','order:create','order:manage',
    'order:price-confirm','tracking:read','exception:read','exception:manage','billing:read','operations:read','audit:read'))
  OR (r.role_code = 'MERCHANT_OPERATOR' AND p.permission_code IN (
    'logistics:read','quote:read','quote:create','quote:validate','order:read','order:create','order:manage',
    'order:price-confirm','tracking:read','exception:read','exception:manage','billing:read','operations:read'))
  OR (r.role_code = 'FINANCE_OPERATOR' AND p.permission_code IN ('finance:bill-import','finance:reconcile'))
  OR (r.role_code = 'WAREHOUSE_OPERATOR' AND p.permission_code IN ('order:read','tracking:read'))
  OR (r.role_code = 'CUSTOMER_SERVICE_OPERATOR' AND p.permission_code IN ('tracking:read','exception:read','exception:manage'))
)
WHERE r.role_scope = 'TENANT'
  AND r.deleted = 0
  AND r.status = 'ACTIVE'
  AND r.role_code IN ('MERCHANT_ADMIN', 'MERCHANT_OPERATOR', 'FINANCE_OPERATOR',
                      'WAREHOUSE_OPERATOR', 'CUSTOMER_SERVICE_OPERATOR');
