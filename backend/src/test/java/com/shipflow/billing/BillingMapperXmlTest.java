package com.shipflow.billing;

import org.junit.jupiter.api.Test;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class BillingMapperXmlTest {
    @Test void mapperUsesTenantScopeV023FieldsHistoryIdempotencyAndNoFinancialMutation() throws Exception {
        String xml=Files.readString(Path.of("src/main/resources/mapper/billing/BillingMapper.xml"),StandardCharsets.UTF_8);
        assertThat(xml).contains("tenant_id=#{tenantId}","sys_user_store_scope","duplicate_count","raw_line_masked","error_handling_status",
                "reconciliation_action_history","operationId","idempotencyKey","processing_status='SUCCEEDED'","version=version+1",
                "reconciliation_status='PENDING_CONFIRMATION'","current_fee","ORDER BY occurred_at ASC,id ASC",
                "tenantWideBillingRole","merchantOrderAccess","(<include refid=\"tenantWideBillingRole\"/> OR <include refid=\"merchantOrderAccess\"/>)",
                "column=\"file_size\" javaType=\"_long\"", "column=\"duplicate_count\" javaType=\"_int\"",
                "column=\"line_no\" javaType=\"_int\"");
        assertThat(xml.toUpperCase()).doesNotContain("UPDATE SHIPMENT_ORDER","UPDATE FEE_ADJUSTMENT","UPDATE SHIPMENT_QUOTE_SNAPSHOT",
                "UPDATE WAREHOUSE_MEASUREMENT","SELECT *");
    }
}
