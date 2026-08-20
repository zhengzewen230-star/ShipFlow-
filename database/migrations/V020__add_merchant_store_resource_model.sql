-- UTF-8, UTC. Adds tenant-scoped store address and public-channel bindings.
-- This migration intentionally does not backfill existing stores.

ALTER TABLE merchant_store
    ADD UNIQUE KEY uk_store_tenant_id (tenant_id, id);

CREATE TABLE merchant_store_address (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '店铺地址配置主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    store_id BIGINT NOT NULL COMMENT '店铺ID',
    address_code VARCHAR(64) NOT NULL COMMENT '店铺内地址编码',
    contact_name VARCHAR(128) NOT NULL COMMENT '联系人姓名',
    company_name VARCHAR(128) NULL COMMENT '公司名称',
    phone VARCHAR(64) NOT NULL COMMENT '联系电话',
    email VARCHAR(128) NULL COMMENT '电子邮箱',
    country_code CHAR(2) NOT NULL COMMENT 'ISO 3166-1 alpha-2国家编码',
    state_province VARCHAR(128) NULL COMMENT '州/省',
    city VARCHAR(128) NOT NULL COMMENT '城市',
    district VARCHAR(128) NULL COMMENT '区县',
    address_line1 VARCHAR(255) NOT NULL COMMENT '地址第一行',
    address_line2 VARCHAR(255) NULL COMMENT '地址第二行',
    postal_code VARCHAR(32) NOT NULL COMMENT '邮政编码',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '地址状态：ACTIVE、DISABLED',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认发货地址',
    default_store_key BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' AND is_default = 1 THEN store_id ELSE NULL END) STORED,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_by BIGINT NULL COMMENT '创建人用户ID',
    updated_by BIGINT NULL COMMENT '最后更新人用户ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_address_code (tenant_id, store_id, address_code),
    UNIQUE KEY uk_store_default_address (tenant_id, default_store_key),
    KEY idx_store_address_status (tenant_id, store_id, status),
    CONSTRAINT fk_store_address_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_store_address_store FOREIGN KEY (tenant_id, store_id) REFERENCES merchant_store (tenant_id, id),
    CONSTRAINT fk_store_address_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_store_address_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id),
    CONSTRAINT chk_store_address_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_store_address_default CHECK (is_default IN (0, 1)),
    CONSTRAINT chk_store_address_country CHECK (country_code REGEXP '^[A-Z]{2}$')
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户店铺默认发货地址配置';

CREATE TABLE merchant_store_channel (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '店铺渠道关联主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    store_id BIGINT NOT NULL COMMENT '店铺ID',
    channel_id BIGINT NOT NULL COMMENT '平台公共物流渠道ID',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '关联状态：ACTIVE、DISABLED',
    is_default TINYINT(1) NOT NULL DEFAULT 0 COMMENT '是否默认物流渠道',
    default_store_key BIGINT GENERATED ALWAYS AS (CASE WHEN status = 'ACTIVE' AND is_default = 1 THEN store_id ELSE NULL END) STORED,
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_by BIGINT NULL COMMENT '创建人用户ID',
    updated_by BIGINT NULL COMMENT '最后更新人用户ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_channel (tenant_id, store_id, channel_id),
    UNIQUE KEY uk_store_default_channel (tenant_id, default_store_key),
    KEY idx_store_channel_status (tenant_id, store_id, status),
    CONSTRAINT fk_store_channel_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_store_channel_store FOREIGN KEY (tenant_id, store_id) REFERENCES merchant_store (tenant_id, id),
    CONSTRAINT fk_store_channel_channel FOREIGN KEY (channel_id) REFERENCES logistics_channel (id),
    CONSTRAINT fk_store_channel_created_by FOREIGN KEY (created_by) REFERENCES sys_user (id),
    CONSTRAINT fk_store_channel_updated_by FOREIGN KEY (updated_by) REFERENCES sys_user (id),
    CONSTRAINT chk_store_channel_status CHECK (status IN ('ACTIVE', 'DISABLED')),
    CONSTRAINT chk_store_channel_default CHECK (is_default IN (0, 1))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户店铺物流渠道关联';
