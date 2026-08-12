package com.shipflow.operations;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class OperationsMapperXmlTest {
    @Test void aggregateQueriesAreTenantScopedAndReadOnly() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/operations/OperationsMapper.xml"));
        assertThat(xml).contains("tenant_id=#{tenantId}", "reconciliation_record", "exception_case", "claim_record").doesNotContain("<insert", "<update", "<delete", "SELECT *");
    }
}
