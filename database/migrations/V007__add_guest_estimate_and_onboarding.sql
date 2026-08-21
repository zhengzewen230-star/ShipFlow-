-- UTF-8, UTC. Public leads are deliberately separate from tenant-owned quotes and orders.

CREATE TABLE guest_estimate_lead (
    id BIGINT NOT NULL AUTO_INCREMENT,
    reference_no VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NULL,
    request_hash CHAR(64) NOT NULL,
    origin_country CHAR(2) NOT NULL,
    destination_country CHAR(2) NOT NULL,
    transport_mode VARCHAR(32) NOT NULL,
    cargo_type VARCHAR(64) NOT NULL,
    declared_weight DECIMAL(18,3) NOT NULL,
    declared_volume DECIMAL(18,6) NOT NULL,
    contact_name VARCHAR(128) NOT NULL,
    business_email VARCHAR(254) NOT NULL,
    contact_phone VARCHAR(64) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_guest_estimate_reference (reference_no), UNIQUE KEY uk_guest_estimate_idempotency (idempotency_key),
    CONSTRAINT chk_guest_estimate_mode CHECK (transport_mode IN ('OCEAN','AIR','ROAD','RAIL','COURIER')),
    CONSTRAINT chk_guest_estimate_status CHECK (status IN ('RECEIVED'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Anonymous pre-quote lead; never a tenant quote';

CREATE TABLE merchant_onboarding_application (
    id BIGINT NOT NULL AUTO_INCREMENT,
    application_no VARCHAR(32) NOT NULL,
    idempotency_key VARCHAR(128) NULL,
    request_hash CHAR(64) NOT NULL,
    company_name VARCHAR(128) NOT NULL,
    contact_name VARCHAR(128) NOT NULL,
    business_email VARCHAR(254) NOT NULL,
    contact_phone VARCHAR(64) NOT NULL,
    country_code CHAR(2) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    review_remark VARCHAR(500) NULL,
    reviewed_by_user_id BIGINT NULL,
    reviewed_at DATETIME(3) NULL,
    tenant_id BIGINT NULL,
    initial_user_id BIGINT NULL,
    version BIGINT NOT NULL DEFAULT 0,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    updated_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_onboarding_application_no (application_no), UNIQUE KEY uk_onboarding_idempotency (idempotency_key),
    KEY idx_onboarding_status_created (status,created_at),
    CONSTRAINT chk_onboarding_status CHECK (status IN ('PENDING','APPROVED','REJECTED')),
    CONSTRAINT fk_onboarding_reviewer FOREIGN KEY (reviewed_by_user_id) REFERENCES sys_user(id),
    CONSTRAINT fk_onboarding_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_onboarding_initial_user FOREIGN KEY (initial_user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='Platform-reviewed merchant onboarding application';

CREATE TABLE onboarding_invitation (
    id BIGINT NOT NULL AUTO_INCREMENT,
    application_id BIGINT NOT NULL,
    tenant_id BIGINT NOT NULL,
    user_id BIGINT NOT NULL,
    token_hash CHAR(64) NOT NULL,
    expires_at DATETIME(3) NOT NULL,
    used_at DATETIME(3) NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id), UNIQUE KEY uk_onboarding_invitation_application (application_id), UNIQUE KEY uk_onboarding_invitation_hash (token_hash),
    KEY idx_onboarding_invitation_active (expires_at,used_at),
    CONSTRAINT fk_onboarding_invitation_application FOREIGN KEY (application_id) REFERENCES merchant_onboarding_application(id),
    CONSTRAINT fk_onboarding_invitation_tenant FOREIGN KEY (tenant_id) REFERENCES tenant(id),
    CONSTRAINT fk_onboarding_invitation_user FOREIGN KEY (user_id) REFERENCES sys_user(id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='One-time hashed invitation token for first tenant administrator activation';
