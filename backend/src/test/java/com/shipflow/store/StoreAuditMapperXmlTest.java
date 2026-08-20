package com.shipflow.store;

import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import static org.assertj.core.api.Assertions.assertThat;

class StoreAuditMapperXmlTest {
    @Test void recentStoreAuditSummaryIsTenantAndResourceScoped() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/store/StoreAuditMapper.xml"));
        assertThat(xml).contains("tenant_id=#{tenantId}", "resource_type='store'", "resource_id=#{storeId}", "ORDER BY occurred_at DESC");
        assertThat(xml).doesNotContain("detail", "Authorization", "Cookie", "Token");
    }
}
