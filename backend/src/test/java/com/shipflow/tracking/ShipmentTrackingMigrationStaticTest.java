package com.shipflow.tracking;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class ShipmentTrackingMigrationStaticTest {
    @Test
    void createsTenantScopedUnifiedTrackingEventWithIdempotentStageKey() throws Exception {
        String sql = Files.readString(Path.of("../database/migrations/V017__create_shipment_tracking_event.sql"));
        assertThat(sql).contains("CREATE TABLE shipment_tracking_event", "order_id BIGINT NOT NULL",
                        "order_no VARCHAR(64) NOT NULL", "waybill_no VARCHAR(64)", "status_code VARCHAR(32) NOT NULL",
                        "source VARCHAR(20)", "occurred_at DATETIME NOT NULL", "idx_order_no", "idx_waybill_no",
                        "tenant_id BIGINT NOT NULL", "uk_shipment_tracking_event_stage")
                .doesNotContain("SELECT *");
    }
}
