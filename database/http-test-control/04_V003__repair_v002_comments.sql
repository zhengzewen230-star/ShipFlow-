-- ShipFlow shipflow_http_test controlled copy of database/migrations/V003__repair_v002_comments.sql.
-- Execute only through a connection whose selected database is shipflow_http_test; no database switching occurs in this file.

SET NAMES utf8mb4 COLLATE utf8mb4_0900_ai_ci;

-- 仅修复 V002 两张表的中文注释，不改变字段、索引、外键、约束或业务数据。
-- 执行前请确保客户端连接使用 utf8mb4，并已执行 SET NAMES utf8mb4。

ALTER TABLE api_idempotency_record
    COMMENT = '通用API幂等记录',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '幂等记录主键',
    MODIFY COLUMN scope_tenant_id BIGINT NOT NULL COMMENT '幂等作用域，0表示平台作用域',
    MODIFY COLUMN operation_id VARCHAR(128) NOT NULL COMMENT '接口操作标识',
    MODIFY COLUMN idempotency_key VARCHAR(128) NOT NULL COMMENT '请求幂等键',
    MODIFY COLUMN http_method VARCHAR(16) NOT NULL COMMENT 'HTTP方法',
    MODIFY COLUMN request_path VARCHAR(255) NOT NULL COMMENT '规范化请求路径，用于摘要和审计',
    MODIFY COLUMN request_hash CHAR(64) NOT NULL COMMENT '规范化请求摘要',
    MODIFY COLUMN processing_status VARCHAR(32) NOT NULL COMMENT '处理状态',
    MODIFY COLUMN response_status INT NULL COMMENT '原始响应HTTP状态码',
    MODIFY COLUMN response_body JSON NULL COMMENT '脱敏后的响应体',
    MODIFY COLUMN resource_type VARCHAR(64) NULL COMMENT '资源类型',
    MODIFY COLUMN resource_id BIGINT NULL COMMENT '资源ID',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '幂等记录过期时间，UTC',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC';

ALTER TABLE auth_refresh_session
    COMMENT = 'Refresh Token会话摘要',
    MODIFY COLUMN id BIGINT NOT NULL AUTO_INCREMENT COMMENT '刷新会话主键',
    MODIFY COLUMN user_id BIGINT NOT NULL COMMENT '用户ID',
    MODIFY COLUMN tenant_id BIGINT NULL COMMENT '租户ID，平台用户可为空',
    MODIFY COLUMN token_hash CHAR(64) NOT NULL COMMENT 'Refresh Token摘要，不保存明文',
    MODIFY COLUMN family_id CHAR(36) NOT NULL COMMENT 'Refresh Token族ID',
    MODIFY COLUMN previous_session_id BIGINT NULL COMMENT '前一刷新会话ID',
    MODIFY COLUMN status VARCHAR(32) NOT NULL COMMENT '会话状态',
    MODIFY COLUMN expires_at DATETIME(3) NOT NULL COMMENT '过期时间，UTC',
    MODIFY COLUMN revoked_at DATETIME(3) NULL COMMENT '撤销时间，UTC',
    MODIFY COLUMN created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，UTC',
    MODIFY COLUMN updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，UTC';
