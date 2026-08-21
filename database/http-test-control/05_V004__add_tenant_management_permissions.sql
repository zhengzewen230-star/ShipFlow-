-- ShipFlow shipflow_http_test controlled copy of database/migrations/V004__add_tenant_management_permissions.sql.
-- Execute only through a connection whose selected database is shipflow_http_test; no database switching occurs in this file.

-- UTF-8, UTC. Adds only the missing platform tenant-management permissions.
INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'tenant:read', 'Read tenants', 'List and view platform tenants'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'tenant:read');

INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'tenant:manage', 'Manage tenants', 'Update and change platform tenant status'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'tenant:manage');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('tenant:create', 'tenant:read', 'tenant:manage')
WHERE r.tenant_id IS NULL
  AND r.role_scope = 'PLATFORM'
  AND r.role_code = 'PLATFORM_ADMIN'
  AND r.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Verification (read-only):
-- SELECT permission_code FROM sys_permission WHERE permission_code IN ('tenant:create','tenant:read','tenant:manage');
-- SELECT r.role_code, p.permission_code FROM sys_role r JOIN sys_role_permission rp ON rp.role_id=r.id JOIN sys_permission p ON p.id=rp.permission_id WHERE r.role_code='PLATFORM_ADMIN' AND r.tenant_id IS NULL;
