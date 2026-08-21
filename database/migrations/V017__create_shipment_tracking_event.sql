-- Unified lifecycle and sandbox tracking projection. All timestamps are UTC.
CREATE TABLE shipment_tracking_event (
    id BIGINT NOT NULL AUTO_INCREMENT,
    tenant_id BIGINT NOT NULL,
    order_id BIGINT NOT NULL,
    order_no VARCHAR(64) NOT NULL,
    waybill_no VARCHAR(64) NULL,
    status_code VARCHAR(32) NOT NULL,
    title VARCHAR(128) NOT NULL,
    description TEXT NULL,
    location VARCHAR(128) NULL,
    source VARCHAR(20) NOT NULL DEFAULT 'INTERNAL' COMMENT 'INTERNAL / SF_EXPRESS',
    occurred_at DATETIME NOT NULL,
    created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_shipment_tracking_event_stage (tenant_id, order_id, status_code),
    KEY idx_order_no (order_no),
    KEY idx_waybill_no (waybill_no),
    KEY idx_tracking_event_tenant_order_time (tenant_id, order_id, occurred_at),
    CONSTRAINT fk_shipment_tracking_event_tenant FOREIGN KEY (tenant_id) REFERENCES tenant (id),
    CONSTRAINT fk_shipment_tracking_event_order FOREIGN KEY (order_id) REFERENCES shipment_order (id),
    CONSTRAINT chk_shipment_tracking_event_source CHECK (source IN ('INTERNAL', 'SF_EXPRESS'))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_0900_ai_ci COMMENT='统一物流轨迹事件';
