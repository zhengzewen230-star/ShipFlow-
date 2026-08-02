USE shipflow;

CREATE TABLE api_idempotency_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '幂等记录主键',
    scope_tenant_id BIGINT NOT NULL COMMENT '幂等作用域，0表示平台作用域',
    operation_id VARCHAR(128) NOT NULL COMMENT '接口操作标识',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '请求幂等键',
    http_method VARCHAR(16) NOT NULL COMMENT 'HTTP方法',
    request_path VARCHAR(255) NOT NULL COMMENT '规范化请求路径，用于摘要和审计',
    request_hash CHAR(64) NOT NULL COMMENT '规范化请求摘要',
    processing_status VARCHAR(32) NOT NULL COMMENT '处理状态',
    response_status INT NULL COMMENT '原始响应HTTP状态码',
    response_body JSON NULL COMMENT '脱敏后的响应体',
    resource_type VARCHAR(64) NULL COMMENT '资源类型',
    resource_id BIGINT NULL COMMENT '资源ID',
    expires_at DATETIME(3) NOT NULL COMMENT '幂等记录过期时间，UTC',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_api_idempotency_scope_operation_key (scope_tenant_id, operation_id, idempotency_key),
    KEY idx_api_idempotency_status_expiry (processing_status, expires_at),
    KEY idx_api_idempotency_resource (resource_type, resource_id),
    KEY idx_api_idempotency_expiry (expires_at),
    CONSTRAINT chk_api_idempotency_status CHECK (processing_status IN ('PROCESSING', 'SUCCEEDED', 'FAILED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='通用API幂等记录';

CREATE TABLE auth_refresh_session (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '刷新会话主键',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    tenant_id BIGINT NULL COMMENT '租户ID，平台用户可为空',
    token_hash CHAR(64) NOT NULL COMMENT 'Refresh Token摘要，不保存明文',
    family_id CHAR(36) NOT NULL COMMENT 'Refresh Token族ID',
    previous_session_id BIGINT NULL COMMENT '前一刷新会话ID',
    status VARCHAR(32) NOT NULL COMMENT '会话状态',
    expires_at DATETIME(3) NOT NULL COMMENT '过期时间，UTC',
    revoked_at DATETIME(3) NULL COMMENT '撤销时间，UTC',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_refresh_token_hash (token_hash),
    UNIQUE KEY uk_refresh_previous_session (previous_session_id),
    KEY idx_refresh_user_status_expiry (user_id, status, expires_at),
    KEY idx_refresh_family_status (family_id, status),
    KEY idx_refresh_expiry (expires_at),
    CONSTRAINT fk_refresh_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_refresh_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_refresh_previous FOREIGN KEY (previous_session_id) REFERENCES auth_refresh_session (id),
    CONSTRAINT chk_refresh_status CHECK (status IN ('ACTIVE', 'ROTATED', 'REVOKED', 'EXPIRED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Refresh Token会话摘要';
