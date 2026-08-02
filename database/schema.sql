CREATE DATABASE IF NOT EXISTS shipflow
  DEFAULT CHARACTER SET utf8mb4
  DEFAULT COLLATE utf8mb4_0900_ai_ci;

USE shipflow;

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS audit_log;
DROP TABLE IF EXISTS reconciliation_record;
DROP TABLE IF EXISTS bill_detail;
DROP TABLE IF EXISTS bill_import_batch;
DROP TABLE IF EXISTS warehouse_outbound_record;
DROP TABLE IF EXISTS claim_record;
DROP TABLE IF EXISTS exception_case;
DROP TABLE IF EXISTS tracking_event;
DROP TABLE IF EXISTS fee_adjustment;
DROP TABLE IF EXISTS warehouse_measurement;
DROP TABLE IF EXISTS shipment_item;
DROP TABLE IF EXISTS shipment_address;
DROP TABLE IF EXISTS shipment_package;
DROP TABLE IF EXISTS shipment_quote_snapshot;
DROP TABLE IF EXISTS shipment_order;
DROP TABLE IF EXISTS quote;
DROP TABLE IF EXISTS price_rule_tier;
DROP TABLE IF EXISTS price_rule;
DROP TABLE IF EXISTS logistics_channel_service_country;
DROP TABLE IF EXISTS logistics_channel;
DROP TABLE IF EXISTS logistics_provider;
DROP TABLE IF EXISTS sys_role_permission;
DROP TABLE IF EXISTS sys_user_role;
DROP TABLE IF EXISTS sys_permission;
DROP TABLE IF EXISTS sys_role;
DROP TABLE IF EXISTS sys_user;
DROP TABLE IF EXISTS merchant_store;
DROP TABLE IF EXISTS tenant;

SET FOREIGN_KEY_CHECKS = 1;

CREATE TABLE tenant (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '租户主键',
    tenant_code VARCHAR(64) NOT NULL COMMENT '租户编码',
    tenant_name VARCHAR(128) NOT NULL COMMENT '签约商家主体名称',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '租户状态：PENDING、ACTIVE、DISABLED',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除、1已删除',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tenant_code (tenant_code),
    KEY idx_tenant_status (status),
    CONSTRAINT chk_tenant_status CHECK (status IN ('PENDING', 'ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='签约商家租户';

CREATE TABLE sys_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限主键',
    permission_code VARCHAR(128) NOT NULL COMMENT '权限编码',
    permission_name VARCHAR(128) NOT NULL COMMENT '权限名称',
    description VARCHAR(255) NULL COMMENT '权限说明',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_permission_code (permission_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台公共权限定义';

CREATE TABLE sys_user (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户主键',
    tenant_id BIGINT NULL COMMENT '租户ID，NULL表示平台用户',
    scope_tenant_id BIGINT GENERATED ALWAYS AS (IFNULL(tenant_id, 0)) STORED COMMENT '唯一性作用域，平台用户为0',
    username VARCHAR(128) NOT NULL COMMENT '登录用户名',
    display_name VARCHAR(128) NOT NULL COMMENT '显示名称',
    password_hash VARCHAR(255) NOT NULL COMMENT '测试环境BCrypt密码摘要',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '用户状态：ACTIVE、DISABLED',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除、1已删除',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_scope_username (scope_tenant_id, username),
    KEY idx_user_tenant_status (tenant_id, status),
    CONSTRAINT fk_user_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT chk_user_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台和租户用户';

CREATE TABLE sys_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色主键',
    tenant_id BIGINT NULL COMMENT '租户ID，NULL表示平台角色',
    scope_tenant_id BIGINT GENERATED ALWAYS AS (IFNULL(tenant_id, 0)) STORED COMMENT '唯一性作用域，平台角色为0',
    role_code VARCHAR(128) NOT NULL COMMENT '角色编码',
    role_name VARCHAR(128) NOT NULL COMMENT '角色名称',
    role_scope VARCHAR(32) NOT NULL COMMENT '角色范围：PLATFORM或TENANT',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '角色状态：ACTIVE、DISABLED',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除、1已删除',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_scope_code (scope_tenant_id, role_code),
    KEY idx_role_tenant_scope (tenant_id, role_scope, status),
    CONSTRAINT fk_role_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT chk_role_scope CHECK (role_scope IN ('PLATFORM', 'TENANT')),
    CONSTRAINT chk_role_tenant_scope CHECK ((role_scope = 'PLATFORM' AND tenant_id IS NULL) OR (role_scope = 'TENANT' AND tenant_id IS NOT NULL)),
    CONSTRAINT chk_role_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台或租户角色';

CREATE TABLE sys_user_role (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户角色关系主键',
    tenant_id BIGINT NULL COMMENT '租户ID，平台角色关系为空',
    user_id BIGINT NOT NULL COMMENT '用户ID',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_role (user_id, role_id),
    KEY idx_user_role_tenant (tenant_id, user_id),
    KEY idx_role_user (role_id, user_id),
    CONSTRAINT fk_user_role_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_user_role_user FOREIGN KEY (user_id) REFERENCES sys_user (id),
    CONSTRAINT fk_user_role_role FOREIGN KEY (role_id) REFERENCES sys_role (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='用户角色关系';

CREATE TABLE sys_role_permission (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色权限关系主键',
    role_id BIGINT NOT NULL COMMENT '角色ID',
    permission_id BIGINT NOT NULL COMMENT '权限ID',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_role_permission (role_id, permission_id),
    CONSTRAINT fk_role_permission_role FOREIGN KEY (role_id) REFERENCES sys_role (id),
    CONSTRAINT fk_role_permission_permission FOREIGN KEY (permission_id) REFERENCES sys_permission (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='角色权限关系';

CREATE TABLE merchant_store (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '店铺主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    store_code VARCHAR(64) NOT NULL COMMENT '店铺编码',
    store_name VARCHAR(128) NOT NULL COMMENT '店铺名称',
    platform_code VARCHAR(64) NOT NULL COMMENT '平台编码',
    platform_account VARCHAR(128) NOT NULL COMMENT '平台账号标识',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '店铺状态：ACTIVE、DISABLED',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除、1已删除',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_store_tenant_code (tenant_id, store_code),
    UNIQUE KEY uk_store_platform_account (tenant_id, platform_code, platform_account),
    KEY idx_store_tenant_status (tenant_id, status),
    CONSTRAINT fk_store_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT chk_store_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='租户商家店铺';

CREATE TABLE logistics_provider (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物流商主键',
    provider_code VARCHAR(64) NOT NULL COMMENT '物流商编码',
    provider_name VARCHAR(128) NOT NULL COMMENT '物流商名称',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '物流商状态：ACTIVE、DISABLED',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除、1已删除',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_provider_code (provider_code),
    KEY idx_provider_status (status),
    CONSTRAINT chk_provider_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台公共物流商';

CREATE TABLE logistics_channel (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物流渠道主键',
    provider_id BIGINT NOT NULL COMMENT '物流商ID',
    channel_code VARCHAR(64) NOT NULL COMMENT '渠道编码',
    channel_name VARCHAR(128) NOT NULL COMMENT '渠道名称',
    service_area VARCHAR(255) NOT NULL COMMENT '服务区域说明',
    status VARCHAR(32) NOT NULL DEFAULT 'ACTIVE' COMMENT '渠道状态：ACTIVE、DISABLED',
    deleted TINYINT(1) NOT NULL DEFAULT 0 COMMENT '逻辑删除标记：0未删除、1已删除',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_provider_code (provider_id, channel_code),
    KEY idx_channel_status (status),
    CONSTRAINT fk_channel_provider FOREIGN KEY (provider_id) REFERENCES logistics_provider (id),
    CONSTRAINT chk_channel_status CHECK (status IN ('ACTIVE', 'DISABLED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台公共物流渠道';

CREATE TABLE logistics_channel_service_country (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '渠道服务国家主键',
    channel_id BIGINT NOT NULL COMMENT '物流渠道ID',
    country_code CHAR(2) NOT NULL COMMENT '服务国家ISO国家编码',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_channel_service_country (channel_id, country_code),
    KEY idx_service_country_code (country_code, channel_id),
    CONSTRAINT fk_service_country_channel FOREIGN KEY (channel_id) REFERENCES logistics_channel (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流渠道服务国家';

CREATE TABLE price_rule (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '价格规则主键',
    channel_id BIGINT NOT NULL COMMENT '物流渠道ID',
    version_no INT NOT NULL COMMENT '业务价格规则版本号',
    rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
    currency CHAR(3) NOT NULL COMMENT '币种编码',
    volume_divisor DECIMAL(18,3) NOT NULL COMMENT '体积重量除数，尺寸单位为cm、重量单位为kg',
    rounding_mode VARCHAR(32) NOT NULL COMMENT '重量进位方式：CEILING、ROUND、NONE',
    rounding_increment DECIMAL(18,3) NOT NULL COMMENT '重量进位增量，单位kg',
    status VARCHAR(32) NOT NULL DEFAULT 'DRAFT' COMMENT '规则状态：DRAFT、PUBLISHED、RETIRED',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    effective_from DATETIME(3) NULL COMMENT '业务生效时间，系统时区为UTC',
    effective_to DATETIME(3) NULL COMMENT '业务失效时间，系统时区为UTC',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_price_rule_channel_version (channel_id, version_no),
    KEY idx_price_rule_channel_status (channel_id, status),
    CONSTRAINT fk_price_rule_channel FOREIGN KEY (channel_id) REFERENCES logistics_channel (id),
    CONSTRAINT chk_price_rule_status CHECK (status IN ('DRAFT', 'PUBLISHED', 'RETIRED')),
    CONSTRAINT chk_price_rule_version_no CHECK (version_no > 0),
    CONSTRAINT chk_price_rule_divisor CHECK (volume_divisor > 0),
    CONSTRAINT chk_price_rule_increment CHECK (rounding_increment > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='渠道价格规则业务版本';

CREATE TABLE price_rule_tier (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '价格阶梯主键',
    price_rule_id BIGINT NOT NULL COMMENT '价格规则ID',
    tier_no INT NOT NULL COMMENT '阶梯顺序号',
    min_weight DECIMAL(18,3) NOT NULL COMMENT '最小重量，包含边界，单位kg',
    max_weight DECIMAL(18,3) NULL COMMENT '最大重量，不包含边界，单位kg，最后阶梯可为空',
    billing_mode VARCHAR(32) NOT NULL COMMENT '计费模式：FIXED、FIRST_CONTINUE',
    first_weight DECIMAL(18,3) NULL COMMENT '首重，单位kg',
    first_fee DECIMAL(18,2) NULL COMMENT '首重费用',
    additional_weight DECIMAL(18,3) NULL COMMENT '续重单位，单位kg',
    additional_fee DECIMAL(18,2) NULL COMMENT '续重费用',
    tier_fee DECIMAL(18,2) NULL COMMENT '固定阶梯费用',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_price_rule_tier_no (price_rule_id, tier_no),
    KEY idx_price_rule_tier_weight (price_rule_id, min_weight, max_weight),
    CONSTRAINT fk_price_rule_tier_rule FOREIGN KEY (price_rule_id) REFERENCES price_rule (id),
    CONSTRAINT chk_tier_weight_range CHECK (tier_no > 0 AND min_weight >= 0 AND (max_weight IS NULL OR max_weight > min_weight)),
    CONSTRAINT chk_tier_billing_mode CHECK (billing_mode IN ('FIXED', 'FIRST_CONTINUE')),
    CONSTRAINT chk_tier_fixed_values CHECK ((billing_mode = 'FIXED' AND tier_fee IS NOT NULL AND tier_fee >= 0 AND first_weight IS NULL AND first_fee IS NULL AND additional_weight IS NULL AND additional_fee IS NULL) OR (billing_mode = 'FIRST_CONTINUE' AND first_weight IS NOT NULL AND first_weight > 0 AND first_fee IS NOT NULL AND first_fee >= 0 AND additional_weight IS NOT NULL AND additional_weight > 0 AND additional_fee IS NOT NULL AND additional_fee >= 0 AND tier_fee IS NULL))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='价格规则重量阶梯';

CREATE TABLE quote (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '报价主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    quote_no VARCHAR(64) NOT NULL COMMENT '报价编号',
    store_id BIGINT NOT NULL COMMENT '店铺ID',
    channel_id BIGINT NOT NULL COMMENT '物流渠道ID',
    price_rule_id BIGINT NOT NULL COMMENT '价格规则ID',
    rule_version_no INT NOT NULL COMMENT '报价采用的业务规则版本',
    declared_weight DECIMAL(18,3) NOT NULL COMMENT '商家申报重量，单位kg',
    declared_length DECIMAL(18,3) NOT NULL COMMENT '商家申报长度，单位cm',
    declared_width DECIMAL(18,3) NOT NULL COMMENT '商家申报宽度，单位cm',
    declared_height DECIMAL(18,3) NOT NULL COMMENT '商家申报高度，单位cm',
    declared_volume_weight DECIMAL(18,3) NOT NULL COMMENT '商家申报阶段体积重量，单位kg',
    declared_chargeable_weight DECIMAL(18,3) NOT NULL COMMENT '商家申报阶段计费重量，单位kg',
    amount DECIMAL(18,2) NOT NULL COMMENT '报价金额',
    currency CHAR(3) NOT NULL COMMENT '报价币种',
    fee_detail JSON NOT NULL COMMENT '报价费用明细',
    valid_from DATETIME(3) NOT NULL COMMENT '报价生效时间，系统时区为UTC',
    valid_to DATETIME(3) NOT NULL COMMENT '报价失效时间，系统时区为UTC',
    status VARCHAR(32) NOT NULL DEFAULT 'VALID' COMMENT '报价状态：VALID、EXPIRED、CANCELLED',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本，仅用于草稿或生命周期更新',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间，创建后不可修改',
    PRIMARY KEY (id),
    UNIQUE KEY uk_quote_tenant_no (tenant_id, quote_no),
    KEY idx_quote_tenant_status_valid (tenant_id, status, valid_to),
    KEY idx_quote_channel_rule (channel_id, price_rule_id),
    CONSTRAINT fk_quote_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_quote_store FOREIGN KEY (store_id) REFERENCES merchant_store (id),
    CONSTRAINT fk_quote_channel FOREIGN KEY (channel_id) REFERENCES logistics_channel (id),
    CONSTRAINT fk_quote_price_rule FOREIGN KEY (price_rule_id) REFERENCES price_rule (id),
    CONSTRAINT chk_quote_status CHECK (status IN ('VALID', 'EXPIRED', 'CANCELLED')),
    CONSTRAINT chk_quote_rule_version CHECK (rule_version_no > 0),
    CONSTRAINT chk_quote_measurements CHECK (declared_weight > 0 AND declared_length > 0 AND declared_width > 0 AND declared_height > 0 AND declared_volume_weight >= 0 AND declared_chargeable_weight >= declared_weight AND declared_chargeable_weight >= declared_volume_weight),
    CONSTRAINT chk_quote_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='运费报价记录';

CREATE TABLE shipment_order (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '物流订单主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    order_no VARCHAR(64) NOT NULL COMMENT '物流订单号',
    idempotency_key VARCHAR(128) NOT NULL COMMENT '创建订单幂等键',
    store_id BIGINT NOT NULL COMMENT '店铺ID',
    quote_id BIGINT NOT NULL COMMENT '报价ID，一份报价最多创建一个订单',
    channel_id BIGINT NOT NULL COMMENT '物流渠道ID',
    current_status VARCHAR(64) NOT NULL DEFAULT 'DRAFT' COMMENT '订单状态，见订单状态机',
    origin_country CHAR(2) NOT NULL COMMENT '起运国家编码',
    destination_country CHAR(2) NOT NULL COMMENT '目的国家编码',
    declared_weight DECIMAL(18,3) NOT NULL COMMENT '商家申报重量，单位kg',
    declared_length DECIMAL(18,3) NOT NULL COMMENT '商家申报长度，单位cm',
    declared_width DECIMAL(18,3) NOT NULL COMMENT '商家申报宽度，单位cm',
    declared_height DECIMAL(18,3) NOT NULL COMMENT '商家申报高度，单位cm',
    declared_volume_weight DECIMAL(18,3) NOT NULL COMMENT '商家申报阶段体积重量，单位kg',
    chargeable_weight DECIMAL(18,3) NOT NULL COMMENT '当前计费重量，单位kg',
    estimated_fee DECIMAL(18,2) NOT NULL COMMENT '预计费用',
    current_fee DECIMAL(18,2) NOT NULL COMMENT '当前应付费用',
    confirmed_fee DECIMAL(18,2) NULL COMMENT '商家确认费用',
    currency CHAR(3) NOT NULL COMMENT '费用币种',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_order_tenant_no (tenant_id, order_no),
    UNIQUE KEY uk_order_quote (quote_id),
    UNIQUE KEY uk_order_tenant_idempotency (tenant_id, idempotency_key),
    KEY idx_order_tenant_status_created (tenant_id, current_status, created_at),
    KEY idx_order_tenant_store (tenant_id, store_id, created_at),
    KEY idx_order_channel_status (channel_id, current_status),
    CONSTRAINT fk_order_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_order_store FOREIGN KEY (store_id) REFERENCES merchant_store (id),
    CONSTRAINT fk_order_quote FOREIGN KEY (quote_id) REFERENCES quote (id),
    CONSTRAINT fk_order_channel FOREIGN KEY (channel_id) REFERENCES logistics_channel (id),
    CONSTRAINT chk_order_status CHECK (current_status IN ('DRAFT', 'PENDING_INBOUND', 'INBOUND', 'PENDING_PRICE_CONFIRMATION', 'READY_FOR_OUTBOUND', 'OUTBOUND', 'IN_TRANSIT', 'DELIVERED', 'CANCELLED', 'RETURNED', 'LOST')),
    CONSTRAINT chk_order_measurements CHECK (declared_weight > 0 AND declared_length > 0 AND declared_width > 0 AND declared_height > 0 AND declared_volume_weight >= 0 AND chargeable_weight >= declared_weight AND chargeable_weight >= declared_volume_weight),
    CONSTRAINT chk_order_fees CHECK (estimated_fee >= 0 AND current_fee >= 0 AND (confirmed_fee IS NULL OR confirmed_fee >= 0))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流订单主表';

CREATE TABLE shipment_quote_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '报价快照主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    shipment_order_id BIGINT NOT NULL COMMENT '订单ID',
    quote_id BIGINT NOT NULL COMMENT '原报价ID',
    price_rule_id BIGINT NOT NULL COMMENT '价格规则ID',
    rule_version_no INT NOT NULL COMMENT '规则业务版本号',
    declared_weight DECIMAL(18,3) NOT NULL COMMENT '快照商家申报重量，单位kg',
    declared_length DECIMAL(18,3) NOT NULL COMMENT '快照商家申报长度，单位cm',
    declared_width DECIMAL(18,3) NOT NULL COMMENT '快照商家申报宽度，单位cm',
    declared_height DECIMAL(18,3) NOT NULL COMMENT '快照商家申报高度，单位cm',
    declared_volume_weight DECIMAL(18,3) NOT NULL COMMENT '快照商家申报阶段体积重量，单位kg',
    declared_chargeable_weight DECIMAL(18,3) NOT NULL COMMENT '快照商家申报阶段计费重量，单位kg',
    volume_divisor DECIMAL(18,3) NOT NULL COMMENT '体积重量除数',
    rounding_mode VARCHAR(32) NOT NULL COMMENT '重量进位方式',
    rounding_increment DECIMAL(18,3) NOT NULL COMMENT '重量进位增量，单位kg',
    fee_detail JSON NOT NULL COMMENT '费用明细快照',
    amount DECIMAL(18,2) NOT NULL COMMENT '报价金额快照',
    currency CHAR(3) NOT NULL COMMENT '报价币种快照',
    valid_from DATETIME(3) NOT NULL COMMENT '报价生效时间快照，系统时区为UTC',
    valid_to DATETIME(3) NOT NULL COMMENT '报价失效时间快照，系统时区为UTC',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间，创建后不可修改',
    PRIMARY KEY (id),
    UNIQUE KEY uk_snapshot_order (shipment_order_id),
    KEY idx_snapshot_tenant_order (tenant_id, shipment_order_id),
    CONSTRAINT fk_snapshot_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_snapshot_order FOREIGN KEY (shipment_order_id) REFERENCES shipment_order (id),
    CONSTRAINT fk_snapshot_quote FOREIGN KEY (quote_id) REFERENCES quote (id),
    CONSTRAINT fk_snapshot_price_rule FOREIGN KEY (price_rule_id) REFERENCES price_rule (id),
    CONSTRAINT chk_snapshot_rule_version CHECK (rule_version_no > 0),
    CONSTRAINT chk_snapshot_measurements CHECK (declared_chargeable_weight >= declared_weight AND declared_chargeable_weight >= declared_volume_weight),
    CONSTRAINT chk_snapshot_amount CHECK (amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单不可变报价快照';

CREATE TABLE shipment_package (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单包裹主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    shipment_order_id BIGINT NOT NULL COMMENT '订单ID，第一版一单一包裹',
    package_no VARCHAR(64) NOT NULL COMMENT '包裹编号',
    declared_weight DECIMAL(18,3) NOT NULL COMMENT '商家申报重量，单位kg',
    declared_length DECIMAL(18,3) NOT NULL COMMENT '商家申报长度，单位cm',
    declared_width DECIMAL(18,3) NOT NULL COMMENT '商家申报宽度，单位cm',
    declared_height DECIMAL(18,3) NOT NULL COMMENT '商家申报高度，单位cm',
    declared_volume_weight DECIMAL(18,3) NOT NULL COMMENT '商家申报阶段体积重量，单位kg',
    declared_chargeable_weight DECIMAL(18,3) NOT NULL COMMENT '商家申报阶段计费重量，单位kg',
    package_status VARCHAR(32) NOT NULL DEFAULT 'CREATED' COMMENT '包裹状态：CREATED、INBOUND、OUTBOUND、CLOSED',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_package_order (shipment_order_id),
    UNIQUE KEY uk_package_order_no (shipment_order_id, package_no),
    KEY idx_package_tenant_status (tenant_id, package_status),
    CONSTRAINT fk_package_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_package_order FOREIGN KEY (shipment_order_id) REFERENCES shipment_order (id),
    CONSTRAINT chk_package_status CHECK (package_status IN ('CREATED', 'INBOUND', 'OUTBOUND', 'CLOSED')),
    CONSTRAINT chk_package_measurements CHECK (declared_chargeable_weight >= declared_weight AND declared_chargeable_weight >= declared_volume_weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单单包裹数据';

CREATE TABLE warehouse_outbound_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仓库出库记录主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    shipment_order_id BIGINT NOT NULL COMMENT '订单ID',
    shipment_package_id BIGINT NOT NULL COMMENT '包裹ID',
    provider_id BIGINT NOT NULL COMMENT '物流商ID',
    tracking_no VARCHAR(128) NOT NULL COMMENT '物流单号',
    outbound_by BIGINT NOT NULL COMMENT '出库操作用户ID',
    outbound_at DATETIME(3) NOT NULL COMMENT '业务出库时间，系统时区为UTC',
    handover_remark VARCHAR(1000) NULL COMMENT '物流商交接备注',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间，出库事实不修改',
    PRIMARY KEY (id),
    UNIQUE KEY uk_outbound_order (shipment_order_id),
    UNIQUE KEY uk_outbound_provider_tracking (provider_id, tracking_no),
    KEY idx_outbound_tenant_time (tenant_id, outbound_at),
    CONSTRAINT fk_outbound_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_outbound_order FOREIGN KEY (shipment_order_id) REFERENCES shipment_order (id),
    CONSTRAINT fk_outbound_package FOREIGN KEY (shipment_package_id) REFERENCES shipment_package (id),
    CONSTRAINT fk_outbound_provider FOREIGN KEY (provider_id) REFERENCES logistics_provider (id),
    CONSTRAINT fk_outbound_user FOREIGN KEY (outbound_by) REFERENCES sys_user (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仓库出库及物流商交接记录';

CREATE TABLE shipment_address (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单地址主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    shipment_order_id BIGINT NOT NULL COMMENT '订单ID',
    address_type VARCHAR(16) NOT NULL COMMENT '地址类型：SENDER、RECEIVER',
    contact_name VARCHAR(128) NOT NULL COMMENT '联系人姓名',
    company_name VARCHAR(128) NULL COMMENT '公司名称',
    phone VARCHAR(64) NOT NULL COMMENT '联系电话',
    email VARCHAR(128) NULL COMMENT '电子邮箱',
    country_code CHAR(2) NOT NULL COMMENT '国家编码',
    state_province VARCHAR(128) NULL COMMENT '州或省',
    city VARCHAR(128) NOT NULL COMMENT '城市',
    district VARCHAR(128) NULL COMMENT '区县',
    address_line1 VARCHAR(255) NOT NULL COMMENT '地址第一行',
    address_line2 VARCHAR(255) NULL COMMENT '地址第二行',
    postal_code VARCHAR(32) NOT NULL COMMENT '邮政编码',
    address_snapshot_hash CHAR(64) NULL COMMENT '地址快照摘要',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间，创建后原则上不可修改',
    PRIMARY KEY (id),
    UNIQUE KEY uk_address_order_type (shipment_order_id, address_type),
    KEY idx_address_tenant_order (tenant_id, shipment_order_id),
    CONSTRAINT fk_address_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_address_order FOREIGN KEY (shipment_order_id) REFERENCES shipment_order (id),
    CONSTRAINT chk_address_type CHECK (address_type IN ('SENDER', 'RECEIVER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单寄件人和收件人地址快照';

CREATE TABLE shipment_item (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '订单商品主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    shipment_package_id BIGINT NOT NULL COMMENT '包裹ID',
    item_no INT NOT NULL COMMENT '商品序号',
    product_name VARCHAR(255) NOT NULL COMMENT '商品中文名称',
    product_name_en VARCHAR(255) NOT NULL COMMENT '商品英文名称',
    quantity INT NOT NULL COMMENT '商品数量',
    unit_price DECIMAL(18,2) NOT NULL COMMENT '商品单价',
    currency CHAR(3) NOT NULL COMMENT '商品币种',
    declared_value DECIMAL(18,2) NOT NULL COMMENT '申报价值',
    sku VARCHAR(128) NULL COMMENT '商品SKU',
    material VARCHAR(128) NULL COMMENT '商品材质',
    origin_country CHAR(2) NULL COMMENT '商品原产国',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间，创建后原则上不可修改',
    PRIMARY KEY (id),
    UNIQUE KEY uk_item_package_no (shipment_package_id, item_no),
    KEY idx_item_tenant_package (tenant_id, shipment_package_id),
    CONSTRAINT fk_item_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_item_package FOREIGN KEY (shipment_package_id) REFERENCES shipment_package (id),
    CONSTRAINT chk_item_values CHECK (quantity > 0 AND unit_price >= 0 AND declared_value >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='跨境物流商品申报数据';

CREATE TABLE warehouse_measurement (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '仓库复称主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    shipment_package_id BIGINT NOT NULL COMMENT '包裹ID',
    actual_weight DECIMAL(18,3) NOT NULL COMMENT '仓库实际重量，单位kg',
    actual_length DECIMAL(18,3) NOT NULL COMMENT '仓库实际长度，单位cm',
    actual_width DECIMAL(18,3) NOT NULL COMMENT '仓库实际宽度，单位cm',
    actual_height DECIMAL(18,3) NOT NULL COMMENT '仓库实际高度，单位cm',
    actual_volume_weight DECIMAL(18,3) NOT NULL COMMENT '仓库实际体积重量，单位kg',
    actual_chargeable_weight DECIMAL(18,3) NOT NULL COMMENT '仓库实际计费重量，单位kg',
    measured_by BIGINT NOT NULL COMMENT '复称人员用户ID',
    measured_at DATETIME(3) NOT NULL COMMENT '业务复称时间，系统时区为UTC',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间，历史记录不修改',
    PRIMARY KEY (id),
    KEY idx_measurement_package_time (shipment_package_id, measured_at),
    KEY idx_measurement_tenant_time (tenant_id, measured_at),
    CONSTRAINT fk_measurement_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_measurement_package FOREIGN KEY (shipment_package_id) REFERENCES shipment_package (id),
    CONSTRAINT fk_measurement_user FOREIGN KEY (measured_by) REFERENCES sys_user (id),
    CONSTRAINT chk_measurement_values CHECK (actual_weight > 0 AND actual_length > 0 AND actual_width > 0 AND actual_height > 0 AND actual_volume_weight >= 0 AND actual_chargeable_weight >= actual_weight AND actual_chargeable_weight >= actual_volume_weight)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='仓库实际重量和尺寸复称记录';

CREATE TABLE fee_adjustment (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '费用调整主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    shipment_order_id BIGINT NOT NULL COMMENT '订单ID',
    warehouse_measurement_id BIGINT NULL COMMENT '复称记录ID，人工调整可为空',
    adjustment_type VARCHAR(32) NOT NULL COMMENT '调整类型：DECREASE、INCREASE、MANUAL',
    before_amount DECIMAL(18,2) NOT NULL COMMENT '调整前费用',
    after_amount DECIMAL(18,2) NOT NULL COMMENT '调整后费用',
    difference_amount DECIMAL(18,2) NOT NULL COMMENT '费用差额',
    currency CHAR(3) NOT NULL COMMENT '费用币种',
    reason VARCHAR(255) NOT NULL COMMENT '调整原因',
    confirmed_by BIGINT NULL COMMENT '确认人员用户ID',
    confirmed_at DATETIME(3) NULL COMMENT '确认时间，系统时区为UTC',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间，历史记录不修改',
    PRIMARY KEY (id),
    UNIQUE KEY uk_adjustment_measurement (warehouse_measurement_id),
    KEY idx_adjustment_tenant_order (tenant_id, shipment_order_id, created_at),
    CONSTRAINT fk_adjustment_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_adjustment_order FOREIGN KEY (shipment_order_id) REFERENCES shipment_order (id),
    CONSTRAINT fk_adjustment_measurement FOREIGN KEY (warehouse_measurement_id) REFERENCES warehouse_measurement (id),
    CONSTRAINT fk_adjustment_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES sys_user (id),
    CONSTRAINT chk_adjustment_type CHECK (adjustment_type IN ('DECREASE', 'INCREASE', 'MANUAL')),
    CONSTRAINT chk_adjustment_amounts CHECK (before_amount >= 0 AND after_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单费用调整记录';

CREATE TABLE tracking_event (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '轨迹事件主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    provider_id BIGINT NOT NULL COMMENT '物流商ID',
    shipment_order_id BIGINT NOT NULL COMMENT '订单ID',
    tracking_no VARCHAR(128) NOT NULL COMMENT '物流单号',
    event_id VARCHAR(128) NOT NULL COMMENT '物流商事件ID，不能为空',
    event_code VARCHAR(64) NOT NULL COMMENT '轨迹事件编码',
    event_description VARCHAR(255) NULL COMMENT '轨迹事件描述',
    event_time DATETIME(3) NOT NULL COMMENT '物流商业务事件时间，系统时区为UTC',
    received_time DATETIME(3) NOT NULL COMMENT '系统接收时间，系统时区为UTC',
    raw_payload JSON NOT NULL COMMENT '物流商回调原始报文',
    process_status VARCHAR(32) NOT NULL DEFAULT 'PENDING' COMMENT '处理状态：PENDING、PROCESSED、RETRY、REJECTED',
    process_message VARCHAR(255) NULL COMMENT '处理结果或异常说明',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间，事件历史不修改',
    PRIMARY KEY (id),
    UNIQUE KEY uk_tracking_provider_no_event (provider_id, tracking_no, event_id),
    KEY idx_tracking_tenant_order_time (tenant_id, shipment_order_id, event_time),
    KEY idx_tracking_no_time (tracking_no, event_time),
    CONSTRAINT fk_tracking_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_tracking_provider FOREIGN KEY (provider_id) REFERENCES logistics_provider (id),
    CONSTRAINT fk_tracking_order FOREIGN KEY (shipment_order_id) REFERENCES shipment_order (id),
    CONSTRAINT chk_tracking_process_status CHECK (process_status IN ('PENDING', 'PROCESSED', 'RETRY', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流轨迹回调事件';

CREATE TABLE exception_case (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '异常处理单主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    shipment_order_id BIGINT NOT NULL COMMENT '订单ID',
    exception_no VARCHAR(64) NOT NULL COMMENT '异常单号',
    exception_type VARCHAR(32) NOT NULL COMMENT '异常类型：ADDRESS、CUSTOMS、TRANSPORT、OTHER',
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '异常状态：OPEN、PROCESSING、RESOLVED、CLOSED',
    description VARCHAR(1000) NOT NULL COMMENT '异常描述',
    reported_at DATETIME(3) NOT NULL COMMENT '异常发生或报告时间，系统时区为UTC',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_exception_tenant_no (tenant_id, exception_no),
    KEY idx_exception_tenant_status (tenant_id, status, reported_at),
    CONSTRAINT fk_exception_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_exception_order FOREIGN KEY (shipment_order_id) REFERENCES shipment_order (id),
    CONSTRAINT chk_exception_type CHECK (exception_type IN ('ADDRESS', 'CUSTOMS', 'TRANSPORT', 'OTHER')),
    CONSTRAINT chk_exception_status CHECK (status IN ('OPEN', 'PROCESSING', 'RESOLVED', 'CLOSED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='订单异常处理单';

CREATE TABLE claim_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '索赔记录主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    exception_case_id BIGINT NOT NULL COMMENT '异常单ID',
    claim_no VARCHAR(64) NOT NULL COMMENT '索赔单号',
    status VARCHAR(32) NOT NULL DEFAULT 'OPEN' COMMENT '索赔状态：OPEN、SUBMITTED、APPROVED、REJECTED、CLOSED',
    claim_amount DECIMAL(18,2) NOT NULL COMMENT '索赔金额',
    currency CHAR(3) NOT NULL COMMENT '索赔币种',
    submitted_at DATETIME(3) NULL COMMENT '提交时间，系统时区为UTC',
    resolved_at DATETIME(3) NULL COMMENT '处理完成时间，系统时区为UTC',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_tenant_no (tenant_id, claim_no),
    UNIQUE KEY uk_claim_exception (exception_case_id),
    KEY idx_claim_tenant_status (tenant_id, status),
    CONSTRAINT fk_claim_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_claim_exception FOREIGN KEY (exception_case_id) REFERENCES exception_case (id),
    CONSTRAINT chk_claim_status CHECK (status IN ('OPEN', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CLOSED')),
    CONSTRAINT chk_claim_amount CHECK (claim_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='异常件索赔记录';

CREATE TABLE bill_import_batch (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '账单导入批次主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    provider_id BIGINT NOT NULL COMMENT '物流商ID',
    batch_no VARCHAR(64) NOT NULL COMMENT '导入批次号',
    file_name VARCHAR(255) NOT NULL COMMENT 'CSV文件名',
    file_hash CHAR(64) NOT NULL COMMENT '文件SHA-256摘要',
    file_size BIGINT NOT NULL COMMENT '文件字节数',
    total_count INT NOT NULL DEFAULT 0 COMMENT '总行数',
    success_count INT NOT NULL DEFAULT 0 COMMENT '成功行数',
    failure_count INT NOT NULL DEFAULT 0 COMMENT '失败行数',
    status VARCHAR(32) NOT NULL DEFAULT 'PROCESSING' COMMENT '批次状态：PROCESSING、PARTIAL_SUCCESS、SUCCESS、FAILED',
    imported_at DATETIME(3) NOT NULL COMMENT '导入业务时间，系统时区为UTC',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bill_batch_tenant_provider_hash (tenant_id, provider_id, file_hash),
    UNIQUE KEY uk_bill_batch_tenant_no (tenant_id, batch_no),
    KEY idx_bill_batch_tenant_time (tenant_id, provider_id, imported_at),
    CONSTRAINT fk_bill_batch_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_bill_batch_provider FOREIGN KEY (provider_id) REFERENCES logistics_provider (id),
    CONSTRAINT chk_bill_batch_status CHECK (status IN ('PROCESSING', 'PARTIAL_SUCCESS', 'SUCCESS', 'FAILED')),
    CONSTRAINT chk_bill_batch_counts CHECK (total_count >= 0 AND success_count >= 0 AND failure_count >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流商账单导入批次';

CREATE TABLE bill_detail (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '账单明细主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    bill_import_batch_id BIGINT NOT NULL COMMENT '导入批次ID',
    provider_id BIGINT NOT NULL COMMENT '物流商ID',
    provider_bill_detail_no VARCHAR(128) NOT NULL COMMENT '物流商账单明细业务编号，不能为空',
    line_no INT NOT NULL COMMENT '文件行号',
    shipment_order_id BIGINT NULL COMMENT '匹配的订单ID，未匹配时为空',
    tracking_no VARCHAR(128) NULL COMMENT '物流单号',
    billed_amount DECIMAL(18,2) NOT NULL COMMENT '物流商账单金额',
    currency CHAR(3) NOT NULL COMMENT '账单币种',
    fee_type VARCHAR(64) NOT NULL COMMENT '费用类型',
    detail_status VARCHAR(32) NOT NULL DEFAULT 'IMPORTED' COMMENT '明细状态：IMPORTED、MATCHED、ERROR',
    error_message VARCHAR(1000) NULL COMMENT '行级错误信息',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间，原始明细不修改',
    PRIMARY KEY (id),
    UNIQUE KEY uk_bill_detail_batch_line (bill_import_batch_id, line_no),
    UNIQUE KEY uk_bill_detail_tenant_provider_no (tenant_id, provider_id, provider_bill_detail_no),
    KEY idx_bill_detail_tenant_order (tenant_id, shipment_order_id),
    KEY idx_bill_detail_tenant_status (tenant_id, detail_status),
    CONSTRAINT fk_bill_detail_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_bill_detail_batch FOREIGN KEY (bill_import_batch_id) REFERENCES bill_import_batch (id),
    CONSTRAINT fk_bill_detail_provider FOREIGN KEY (provider_id) REFERENCES logistics_provider (id),
    CONSTRAINT fk_bill_detail_order FOREIGN KEY (shipment_order_id) REFERENCES shipment_order (id),
    CONSTRAINT chk_bill_detail_status CHECK (detail_status IN ('IMPORTED', 'MATCHED', 'ERROR')),
    CONSTRAINT chk_bill_detail_amount CHECK (billed_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='物流商账单明细';

CREATE TABLE reconciliation_record (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '对账记录主键',
    tenant_id BIGINT NOT NULL COMMENT '租户ID',
    shipment_order_id BIGINT NOT NULL COMMENT '订单ID',
    bill_detail_id BIGINT NOT NULL COMMENT '账单明细ID',
    system_amount DECIMAL(18,2) NOT NULL COMMENT '系统计算金额',
    billed_amount DECIMAL(18,2) NOT NULL COMMENT '物流商账单金额',
    difference_amount DECIMAL(18,2) NOT NULL COMMENT '差异金额',
    reconciliation_status VARCHAR(32) NOT NULL DEFAULT 'PENDING_CONFIRMATION' COMMENT '对账状态：AUTO_CLOSED、PENDING_CONFIRMATION、CONFIRMED、REJECTED',
    resolution_type VARCHAR(64) NULL COMMENT '处理方式',
    confirmed_by BIGINT NULL COMMENT '财务确认人用户ID',
    confirmed_at DATETIME(3) NULL COMMENT '财务确认时间，系统时区为UTC',
    remark VARCHAR(1000) NULL COMMENT '财务处理备注',
    version BIGINT NOT NULL DEFAULT 0 COMMENT '技术乐观锁版本',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间，系统时区为UTC',
    PRIMARY KEY (id),
    UNIQUE KEY uk_reconciliation_bill_detail (bill_detail_id),
    KEY idx_reconciliation_tenant_status (tenant_id, reconciliation_status, created_at),
    KEY idx_reconciliation_tenant_order (tenant_id, shipment_order_id),
    CONSTRAINT fk_reconciliation_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_reconciliation_order FOREIGN KEY (shipment_order_id) REFERENCES shipment_order (id),
    CONSTRAINT fk_reconciliation_detail FOREIGN KEY (bill_detail_id) REFERENCES bill_detail (id),
    CONSTRAINT fk_reconciliation_confirmed_by FOREIGN KEY (confirmed_by) REFERENCES sys_user (id),
    CONSTRAINT chk_reconciliation_status CHECK (reconciliation_status IN ('AUTO_CLOSED', 'PENDING_CONFIRMATION', 'CONFIRMED', 'REJECTED')),
    CONSTRAINT chk_reconciliation_amounts CHECK (system_amount >= 0 AND billed_amount >= 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='费用对账记录';

CREATE TABLE audit_log (
    id BIGINT NOT NULL AUTO_INCREMENT COMMENT '审计日志主键',
    tenant_id BIGINT NULL COMMENT '租户ID，NULL表示平台级审计',
    operator_user_id BIGINT NULL COMMENT '操作用户ID，系统操作可为空',
    action_type VARCHAR(64) NOT NULL COMMENT '操作类型',
    resource_type VARCHAR(64) NOT NULL COMMENT '资源类型',
    resource_id BIGINT NULL COMMENT '资源ID',
    request_id VARCHAR(128) NULL COMMENT '请求关联ID',
    result_status VARCHAR(32) NOT NULL COMMENT '结果状态：SUCCESS、FAILURE、REJECTED',
    reason VARCHAR(1000) NULL COMMENT '操作原因或失败原因',
    detail JSON NULL COMMENT '审计详情',
    occurred_at DATETIME(3) NOT NULL COMMENT '业务发生时间，系统时区为UTC',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间，系统时区为UTC',
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '更新时间，创建后不可修改',
    PRIMARY KEY (id),
    KEY idx_audit_tenant_time (tenant_id, occurred_at),
    KEY idx_audit_resource (resource_type, resource_id),
    KEY idx_audit_request (request_id),
    CONSTRAINT fk_audit_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_audit_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_audit_result CHECK (result_status IN ('SUCCESS', 'FAILURE', 'REJECTED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='平台和租户审计日志，只追加不修改删除';
