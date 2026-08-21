-- UTF-8, UTC. Adds only the missing fee-confirmation workflow state and merchant operator store scope.

ALTER TABLE fee_adjustment
    ADD COLUMN confirmation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_CONFIRMATION' COMMENT '确认状态：PENDING_CONFIRMATION、REQUESTED、CONFIRMED' AFTER reason,
    ADD COLUMN requested_by BIGINT NULL COMMENT '费用确认申请人用户ID' AFTER confirmation_status,
    ADD COLUMN requested_at DATETIME(3) NULL COMMENT '费用确认申请时间，系统时区为UTC' AFTER requested_by,
    ADD CONSTRAINT fk_adjustment_requested_by FOREIGN KEY (requested_by) REFERENCES sys_user (id),
    ADD CONSTRAINT chk_adjustment_confirmation_status CHECK (confirmation_status IN ('PENDING_CONFIRMATION', 'REQUESTED', 'CONFIRMED'));

CREATE TABLE sys_user_store_scope (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户店铺授权主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    user_id BIGINT NOT NULL COMMENT '业务员用户ID',
    store_id BIGINT NOT NULL COMMENT '被授权店铺ID',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '授权状态：ACTIVE、DISABLED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_store_scope (tenant_id, user_id, store_id),
    KEY idx_user_store_scope_user (tenant_id, user_id, status),
    KEY idx_user_store_scope_store (tenant_id, store_id, status),
    CONSTRAINT fk_user_store_scope_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_user_store_scope_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_store_scope_store FOREIGN KEY (store_id) REFERENCES merchant_store (id),
    CONSTRAINT chk_user_store_scope_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='商家业务员店铺授权范围';

INSERT INTO sys_permission (permission_code, permission_name, description)
VALUES ('order:price-request', '申请费用确认', '商家业务员提交订单费用确认申请')
ON DUPLICATE KEY UPDATE permission_name = VALUES(permission_name), description = VALUES(description);

DELETE rp
FROM sys_role_permission rp
JOIN sys_role r ON r.id = rp.role_id
JOIN sys_permission p ON p.id = rp.permission_id
WHERE r.role_scope = 'TENANT'
  AND r.role_code = 'MERCHANT_OPERATOR'
  AND p.permission_code IN ('order:price-confirm', 'order:price-request');

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'order:price-request'
WHERE r.role_scope = 'TENANT' AND r.role_code = 'MERCHANT_OPERATOR'
  AND r.deleted = 0 AND r.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);

INSERT INTO sys_role_permission (role_id, permission_id)
SELECT r.id, p.id
FROM sys_role r
JOIN sys_permission p ON p.permission_code = 'order:price-confirm'
WHERE r.role_scope = 'TENANT' AND r.role_code IN ('MERCHANT_ADMIN', 'FINANCE_OPERATOR')
  AND r.deleted = 0 AND r.status = 'ACTIVE'
  AND NOT EXISTS (SELECT 1 FROM sys_role_permission rp WHERE rp.role_id = r.id AND rp.permission_id = p.id);
