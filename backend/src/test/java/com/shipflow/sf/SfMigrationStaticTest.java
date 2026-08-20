package com.shipflow.sf;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SfMigrationStaticTest {
    @Test
    void migrationStoresOnlyNonSensitiveSupplierState() throws Exception {
        String sql = Files.readString(Path.of("../database/migrations/V013__add_provider_order_and_sf_artifacts.sql"));
        assertThat(sql).contains("CREATE TABLE provider_order", "request_id", "external_order_no", "tracking_no",
                        "CREATE TABLE shipment_label", "CREATE TABLE customs_document")
                .doesNotContain("msgDigest", "checkWord", "raw_payload", "SELECT *");
    }

    @Test
    void supplierOrderLookupRequiresTenantScopedMeasurementBeforeCreate() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/sf/SfProviderOrderMapper.xml"));
        assertThat(xml).contains("id=\"hasMeasurement\"", "warehouse_measurement", "sp.tenant_id=#{tenantId}")
                .doesNotContain("SELECT *");
    }

    @Test
    void warehouseHandoverMigrationIsExplicitAndNotADataWrite() throws Exception {
        String sql = Files.readString(Path.of("../database/migrations/V014__add_warehouse_handover_references.sql"));
        assertThat(sql).contains("ALTER TABLE warehouse_outbound_record", "batch_no", "manifest_reference")
                .doesNotContain("INSERT INTO", "UPDATE ", "DELETE ");
    }

    @Test
    void invoiceReferenceMigrationIsExplicit() throws Exception {
        String sql = Files.readString(Path.of("../database/migrations/V015__add_invoice_reference.sql"));
        assertThat(sql).contains("ALTER TABLE shipment_label", "invoice_reference")
                .doesNotContain("DROP", "DELETE", "SELECT *");
    }

    @Test
    void sandboxRetentionMigrationIsNonDestructiveAndUtcScoped() throws Exception {
        String sql = Files.readString(Path.of("../database/migrations/V016__retain_sf_sandbox_test_orders.sql"));
        assertThat(sql).contains("ALTER TABLE provider_order", "test_flag", "test_retention_until_utc", "UTC")
                .doesNotContain("DROP", "DELETE", "UPDATE ");
    }
}
