-- UTF-8, UTC. V006 grants this existing read permission to tenant roles present at that time.
-- Tenant roles created after V006 need the same public-catalogue permission to load formal-quote channels.

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'logistics:read'
WHERE r.role_scope = 'TENANT'
  AND r.role_code = 'MERCHANT_ADMIN'
  AND r.tenant_id IS NOT NULL
  AND r.deleted = 0
  AND r.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1
      FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
