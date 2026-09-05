package com.shipflow.operations;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class OperationsMapperXmlTest {
    @Test void aggregateQueriesAreTenantScopedAndReadOnly() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/operations/OperationsMapper.xml"));
        assertThat(xml).contains("tenant_id=#{tenantId}", "store_id=#{storeId}", "sys_user_store_scope",
                "sys_role", "LABEL_READY", "reconciliation_record", "exception_case", "claim_record")
                .doesNotContain("<insert", "<update", "<delete", "SELECT *");
    }

    @Test void workbenchQueriesDeclareTheCompleteNamedParameterAndResultContract() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/operations/OperationsMapper.xml"));

        assertThat(xml).contains(
                "<select id=\"findVisibleStoreIds\" resultType=\"long\">",
                "<select id=\"findWorkbenchCounts\" resultType=\"com.shipflow.operations.domain.WorkbenchCounts\">",
                "<select id=\"findRecentOrders\" resultType=\"com.shipflow.operations.domain.WorkbenchRecentOrder\">",
                "<select id=\"findRisks\" resultType=\"com.shipflow.operations.domain.WorkbenchRisk\">",
                "#{tenantId}", "#{userId}", "#{from}", "#{to}", "#{storeId}",
                "sortBy", "sortDirection", "#{offset}", "#{limit}",
                "#{asOf}", "#{thresholdHours}",
                "pending_orders", "pending_inbound", "pending_measurement", "pending_label",
                "order_id", "order_status", "label_status", "resource_id", "occurred_at");
        assertThat(xml.split("&gt;= #\\{from}", -1).length - 1).isGreaterThanOrEqualTo(14);
        assertThat(xml.split("&lt; #\\{to}", -1).length - 1).isGreaterThanOrEqualTo(14);
    }
}
