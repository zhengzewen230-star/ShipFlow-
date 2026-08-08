-- UTF-8, UTC. Permission dictionary only; tenant role bindings are provisioned per new tenant in the service.
INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'store:create', 'Create stores', 'Create stores in the current tenant'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'store:create');
INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'store:read', 'Read stores', 'Read stores in the current tenant'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'store:read');
INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'store:manage', 'Manage stores', 'Update store data and status in the current tenant'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'store:manage');
INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'user:read', 'Read users', 'Read users in the current tenant'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'user:read');
INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'user:manage', 'Manage users', 'Manage users in the current tenant'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'user:manage');
INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'role:read', 'Read roles', 'Read roles in the current tenant'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'role:read');
INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'role:manage', 'Manage roles', 'Manage roles in the current tenant'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'role:manage');
INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'permission:read', 'Read permissions', 'Read the shared permission dictionary'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'permission:read');

-- Bind the tenant permission matrix for existing active tenant roles. Existing
-- relationships are preserved; NOT EXISTS makes these inserts idempotent.
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN (
    'store:create', 'store:read', 'store:manage',
    'user:read', 'user:manage', 'role:read', 'role:manage', 'permission:read'
)
WHERE r.role_code = 'MERCHANT_ADMIN'
  AND r.role_scope = 'TENANT'
  AND r.tenant_id IS NOT NULL
  AND r.deleted = 0
  AND r.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'store:read'
WHERE r.role_code = 'MERCHANT_OPERATOR'
  AND r.role_scope = 'TENANT'
  AND r.tenant_id IS NOT NULL
  AND r.deleted = 0
  AND r.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- V005 does not bind tenant:create, tenant:read, or tenant:manage to any
-- tenant role, and does not modify WAREHOUSE_OPERATOR, FINANCE_OPERATOR,
-- MOCK_LOGISTICS_SYSTEM, or PLATFORM roles.

-- Verification (read-only):
-- 1) All eight dictionary entries exist.
-- SELECT COUNT(*) AS permission_count
-- FROM sys_permission
-- WHERE permission_code IN ('store:create','store:read','store:manage','user:read','user:manage','role:read','role:manage','permission:read');
-- 2) Each active tenant MERCHANT_ADMIN is missing zero of the eight permissions.
-- SELECT r.tenant_id, r.id AS role_id, COUNT(p.permission_code) AS missing_permission_count
-- FROM sys_role r
-- CROSS JOIN sys_permission p
-- LEFT JOIN sys_role_permission rp ON rp.role_id = r.id AND rp.permission_id = p.id
-- WHERE r.role_code = 'MERCHANT_ADMIN' AND r.role_scope = 'TENANT'
--   AND r.tenant_id IS NOT NULL AND r.deleted = 0 AND r.status = 'ACTIVE'
--   AND p.permission_code IN ('store:create','store:read','store:manage','user:read','user:manage','role:read','role:manage','permission:read')
--   AND rp.id IS NULL
-- GROUP BY r.tenant_id, r.id;
-- 3) Each active tenant MERCHANT_OPERATOR has store:read; this migration adds no other new permission.
-- SELECT r.tenant_id, r.id AS role_id,
--        SUM(CASE WHEN p.permission_code = 'store:read' THEN 1 ELSE 0 END) AS store_read_count,
--        SUM(CASE WHEN p.permission_code IN ('store:create','store:manage','user:read','user:manage','role:read','role:manage','permission:read') THEN 1 ELSE 0 END) AS other_new_permission_count
-- FROM sys_role r
-- LEFT JOIN sys_role_permission rp ON rp.role_id = r.id
-- LEFT JOIN sys_permission p ON p.id = rp.permission_id
-- WHERE r.role_code = 'MERCHANT_OPERATOR' AND r.role_scope = 'TENANT'
--   AND r.tenant_id IS NOT NULL AND r.deleted = 0 AND r.status = 'ACTIVE'
-- GROUP BY r.tenant_id, r.id;
-- 4) No tenant role has platform tenant-management permissions.
-- SELECT COUNT(*) AS tenant_platform_permission_count
-- FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
-- JOIN sys_permission p ON p.id = rp.permission_id
-- WHERE r.role_scope = 'TENANT' AND p.permission_code IN ('tenant:create','tenant:read','tenant:manage');
-- 5) V005 must not have added tenant permissions to PLATFORM roles.
-- SELECT r.id, r.role_code, p.permission_code
-- FROM sys_role r JOIN sys_role_permission rp ON rp.role_id = r.id
-- JOIN sys_permission p ON p.id = rp.permission_id
-- WHERE r.role_scope = 'PLATFORM'
--   AND p.permission_code IN ('store:create','store:read','store:manage','user:read','user:manage','role:read','role:manage','permission:read');
-- 6) No duplicate role-permission pairs.
-- SELECT role_id, permission_id, COUNT(*) AS duplicate_count
-- FROM sys_role_permission GROUP BY role_id, permission_id HAVING COUNT(*) > 1;
-- 7) Per-tenant missing permission statistics for active MERCHANT_ADMIN roles.
-- SELECT r.tenant_id, COUNT(DISTINCT r.id) AS admin_role_count,
--        COUNT(DISTINCT r.id) * 8 - COUNT(DISTINCT CONCAT(r.id, ':', p.id)) AS missing_permission_count
-- FROM sys_role r
-- LEFT JOIN sys_role_permission rp ON rp.role_id = r.id
-- LEFT JOIN sys_permission p ON p.id = rp.permission_id
--   AND p.permission_code IN ('store:create','store:read','store:manage','user:read','user:manage','role:read','role:manage','permission:read')
-- WHERE r.role_code = 'MERCHANT_ADMIN' AND r.role_scope = 'TENANT'
--   AND r.tenant_id IS NOT NULL AND r.deleted = 0 AND r.status = 'ACTIVE'
-- GROUP BY r.tenant_id;
