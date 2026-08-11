-- UTF-8, UTC. Adds only the platform logistics-master-data contract.
-- V002--V005 must already be recorded successfully by Flyway.

ALTER TABLE logistics_channel
    ADD COLUMN transport_mode VARCHAR(32) NOT NULL DEFAULT 'COURIER'
        COMMENT 'Transport mode: OCEAN, AIR, ROAD, RAIL, COURIER' AFTER channel_name,
    ADD CONSTRAINT chk_channel_transport_mode
        CHECK (transport_mode IN ('OCEAN', 'AIR', 'ROAD', 'RAIL', 'COURIER'));

INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'logistics:read', 'Read logistics master data', 'Read platform logistics providers and channels'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'logistics:read');

INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'logistics:manage', 'Manage logistics master data', 'Maintain platform logistics providers, channels and service countries'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'logistics:manage');

INSERT INTO sys_permission (permission_code, permission_name, description)
SELECT 'price-rule:manage', 'Manage price rules', 'Create and publish platform channel price-rule versions'
WHERE NOT EXISTS (SELECT 1 FROM sys_permission WHERE permission_code = 'price-rule:manage');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code IN ('logistics:read', 'logistics:manage', 'price-rule:manage')
WHERE r.role_scope = 'PLATFORM'
  AND r.role_code = 'PLATFORM_ADMIN'
  AND r.tenant_id IS NULL
  AND r.deleted = 0
  AND r.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );

-- Tenant callers may read only the public catalogue.  They never receive platform write permissions.
INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'logistics:read'
WHERE r.role_scope = 'TENANT'
  AND r.deleted = 0
  AND r.status = 'ACTIVE'
  AND NOT EXISTS (
      SELECT 1 FROM sys_role_permission rp
      WHERE rp.role_id = r.id AND rp.permission_id = p.id
  );
