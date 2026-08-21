-- Keeps sandbox provider orders traceable for 30 days. No cleanup is performed here.
ALTER TABLE provider_order
    ADD COLUMN test_flag BOOLEAN NOT NULL DEFAULT FALSE COMMENT 'Sandbox test order marker' AFTER retry_count,
    ADD COLUMN test_retention_until_utc DATETIME(3) NULL COMMENT 'UTC retention deadline for sandbox test data' AFTER test_flag;

CREATE INDEX idx_provider_order_tenant_test_retention
    ON provider_order (tenant_id, test_flag, test_retention_until_utc);
