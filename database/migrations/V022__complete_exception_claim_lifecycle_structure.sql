-- UTF-8 / UTC. Pure exception-and-claim lifecycle structure migration.
-- This migration intentionally contains no INSERT, UPDATE, DELETE, history rewrite,
-- or Flyway repair operation.

ALTER TABLE exception_case
    ADD COLUMN assigned_to_user_id BIGINT NULL COMMENT '当前异常处理负责人用户 ID' AFTER responsible_party,
    ADD KEY idx_exception_assignee_tenant_created (assigned_to_user_id, tenant_id, created_at, id),
    ADD KEY idx_exception_tenant_type_created (tenant_id, exception_type, created_at, id),
    ADD KEY idx_exception_tenant_responsible_created (tenant_id, responsible_party, created_at, id),
    ADD KEY idx_exception_tenant_order_created (tenant_id, shipment_order_id, created_at, id),
    ADD CONSTRAINT fk_exception_assigned_user FOREIGN KEY (assigned_to_user_id) REFERENCES sys_user (id);

ALTER TABLE exception_evidence_attachment
    ADD COLUMN description VARCHAR(1000) NULL COMMENT '证据说明，仅返回元数据' AFTER content_type;

ALTER TABLE claim_record
    ADD COLUMN claim_reason VARCHAR(1000) NULL COMMENT '索赔原因' AFTER currency,
    ADD COLUMN resolved_amount DECIMAL(18,2) NULL COMMENT '审核或实际获批金额' AFTER claim_amount,
    ADD COLUMN result_reason VARCHAR(1000) NULL COMMENT '索赔审核结果原因' AFTER resolved_amount,
    ADD COLUMN finance_confirmed_by_user_id BIGINT NULL COMMENT '财务确认操作人' AFTER resolved_at,
    ADD COLUMN finance_confirmed_at DATETIME(3) NULL COMMENT '财务确认时间，UTC' AFTER finance_confirmed_by_user_id,
    ADD KEY idx_claim_finance_confirmed_by (finance_confirmed_by_user_id),
    ADD CONSTRAINT fk_claim_finance_confirmed_user FOREIGN KEY (finance_confirmed_by_user_id) REFERENCES sys_user (id),
    ADD CONSTRAINT chk_claim_resolved_amount CHECK (resolved_amount IS NULL OR (resolved_amount >= 0 AND resolved_amount <= claim_amount));

ALTER TABLE claim_record
    DROP CHECK chk_claim_status,
    ADD CONSTRAINT chk_claim_status CHECK (status IN ('OPEN', 'SUBMITTED', 'APPROVED', 'PARTIALLY_APPROVED', 'REJECTED', 'CLOSED'));

ALTER TABLE audit_log
    ADD KEY idx_audit_tenant_resource_time (tenant_id, resource_type, resource_id, occurred_at, id);

CREATE TABLE claim_evidence_reference (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    claim_record_id BIGINT NOT NULL,
    evidence_attachment_id BIGINT NOT NULL,
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3),
    PRIMARY KEY (id),
    UNIQUE KEY uk_claim_evidence_reference (tenant_id, claim_record_id, evidence_attachment_id),
    KEY idx_claim_evidence_claim_time (tenant_id, claim_record_id, created_at),
    KEY idx_claim_evidence_attachment (evidence_attachment_id),
    CONSTRAINT fk_claim_evidence_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_claim_evidence_claim FOREIGN KEY (claim_record_id) REFERENCES claim_record (id),
    CONSTRAINT fk_claim_evidence_attachment FOREIGN KEY (evidence_attachment_id) REFERENCES exception_evidence_attachment (id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='索赔引用的异常证据附件';
