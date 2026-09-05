-- Phase 10: additive billing and reconciliation lifecycle structure only.
-- Impact: existing business rows are not updated, deleted, or rewritten.
-- Rollback: drop the added indexes/table/columns after application rollback; no data migration is required.

ALTER TABLE bill_import_batch
    ADD COLUMN duplicate_count INT NOT NULL DEFAULT 0 COMMENT 'duplicate rows in this import batch' AFTER failure_count,
    ADD CONSTRAINT chk_bill_batch_duplicate_count CHECK (duplicate_count >= 0),
    ADD CONSTRAINT chk_bill_batch_result_counts CHECK (success_count + failure_count + duplicate_count <= total_count);

ALTER TABLE bill_detail
    ADD COLUMN raw_line_masked VARCHAR(2000) NULL COMMENT 'masked source CSV row, never authoritative financial data' AFTER error_message,
    ADD COLUMN error_handling_status VARCHAR(32) NOT NULL DEFAULT 'NOT_APPLICABLE' COMMENT 'NOT_APPLICABLE, PENDING, RESOLVED, IGNORED' AFTER raw_line_masked,
    ADD CONSTRAINT chk_bill_detail_error_handling_status CHECK (error_handling_status IN ('NOT_APPLICABLE','PENDING','RESOLVED','IGNORED')),
    ADD KEY idx_bill_detail_tenant_error_handling (tenant_id, detail_status, error_handling_status, bill_import_batch_id);

CREATE TABLE reconciliation_action_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    reconciliation_record_id BIGINT NOT NULL,
    action_type VARCHAR(32) NOT NULL COMMENT 'CONFIRM, REJECT, COMMENT, AUTO_CLOSE',
    status_before VARCHAR(32) NULL,
    status_after VARCHAR(32) NULL,
    remark VARCHAR(1000) NULL,
    operator_user_id BIGINT NULL,
    request_id VARCHAR(128) NULL,
    occurred_at DATETIME(3) NOT NULL COMMENT 'UTC',
    PRIMARY KEY (id),
    KEY idx_reconciliation_history_tenant_record_time (tenant_id, reconciliation_record_id, occurred_at, id),
    CONSTRAINT fk_reconciliation_history_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_reconciliation_history_record FOREIGN KEY (reconciliation_record_id) REFERENCES reconciliation_record (id),
    CONSTRAINT fk_reconciliation_history_operator FOREIGN KEY (operator_user_id) REFERENCES sys_user (id),
    CONSTRAINT chk_reconciliation_history_action CHECK (action_type IN ('CONFIRM','REJECT','COMMENT','AUTO_CLOSE'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='reconciliation manual action history';
