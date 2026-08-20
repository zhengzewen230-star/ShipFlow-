-- UTF-8, UTC. Adds structured exception responsibility, handling history and evidence attachments.

ALTER TABLE exception_case
    ADD COLUMN responsible_party VARCHAR(32) NULL COMMENT '责任方：MERCHANT、PROVIDER、CUSTOMS、CUSTOMER、OTHER' AFTER description,
    ADD CONSTRAINT chk_exception_responsible_party CHECK (responsible_party IS NULL OR responsible_party IN ('MERCHANT', 'PROVIDER', 'CUSTOMS', 'CUSTOMER', 'OTHER')),
    DROP CHECK chk_exception_status,
    ADD CONSTRAINT chk_exception_status CHECK (status IN ('OPEN', 'PROCESSING', 'WAITING_PROVIDER_FEEDBACK', 'RESOLVED', 'PENDING_FINANCE_CONFIRMATION', 'CLOSED'));

CREATE TABLE exception_handling_record (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    exception_case_id BIGINT NOT NULL,
    record_no VARCHAR(64) NOT NULL,
    handled_by_user_id BIGINT NOT NULL,
    record_type VARCHAR(32) NOT NULL,
    content VARCHAR(4000) NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exception_handling_tenant_no (tenant_id, record_no),
    KEY idx_exception_handling_case_time (tenant_id, exception_case_id, created_at),
    CONSTRAINT fk_exception_handling_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_exception_handling_case FOREIGN KEY (exception_case_id) REFERENCES exception_case (id),
    CONSTRAINT fk_exception_handling_user FOREIGN KEY (handled_by_user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_exception_handling_type CHECK (record_type IN ('CONTACT', 'FOLLOW_UP', 'PROVIDER_FEEDBACK', 'INTERNAL_NOTE', 'OTHER'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;

CREATE TABLE exception_evidence_attachment (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    exception_case_id BIGINT NOT NULL,
    uploaded_by_user_id BIGINT NOT NULL,
    original_file_name VARCHAR(255) NOT NULL,
    content_type VARCHAR(128) NOT NULL,
    file_size BIGINT NOT NULL,
    content_sha256 CHAR(64) NOT NULL,
    content_blob LONGBLOB NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_exception_evidence_content (tenant_id, exception_case_id, content_sha256),
    KEY idx_exception_evidence_case_time (tenant_id, exception_case_id, created_at),
    CONSTRAINT fk_exception_evidence_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_exception_evidence_case FOREIGN KEY (exception_case_id) REFERENCES exception_case (id),
    CONSTRAINT fk_exception_evidence_user FOREIGN KEY (uploaded_by_user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_exception_evidence_size CHECK (file_size > 0)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci;
