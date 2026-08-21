-- UTF-8, UTC. Safe repair for environments where V009 ran before V006.
-- V009 joins the permission dictionary, so it cannot bind a permission that is absent.

INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'logistics:read', 'Read logistics master data', 'Read platform logistics providers and channels'
WHERE NOT EXISTS (
    SELECT 1 FROM sys_permission WHERE permission_code = 'logistics:read'
);

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
