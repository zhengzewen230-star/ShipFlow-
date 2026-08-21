package com.shipflow.sf;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class SfProviderOrderMapperXmlTest {
    @Test
    void usesExplicitTenantScopedColumnsAndSupplierStateOnly() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/sf/SfProviderOrderMapper.xml"));
        assertThat(xml).contains("provider_order", "shipment_order", "tenant_id=#{tenantId}", "request_id=#{requestId}")
                .contains("external_order_no", "tracking_no", "findAddress", "findItems", "shipment_label", "customs_document")
                .doesNotContain("SELECT *", "msgDigest", "checkWord", "raw_payload");
    }
}
