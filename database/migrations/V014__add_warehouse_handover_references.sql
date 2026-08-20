-- Not executed in this change. Requires a controlled Flyway run after review.
-- Adds non-sensitive warehouse handover references required by the operation detail page.
ALTER TABLE warehouse_outbound_record
    ADD COLUMN batch_no VARCHAR(128) NULL COMMENT '仓库交接批次号' AFTER tracking_no,
    ADD COLUMN manifest_reference VARCHAR(255) NULL COMMENT '交接清单引用' AFTER batch_no;

CREATE INDEX idx_outbound_tenant_batch
    ON warehouse_outbound_record (tenant_id, batch_no);
