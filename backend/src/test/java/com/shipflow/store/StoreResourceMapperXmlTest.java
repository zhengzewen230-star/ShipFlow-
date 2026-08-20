package com.shipflow.store;

import com.shipflow.store.mapper.StoreResourceMapper;
import org.apache.ibatis.annotations.Param;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StoreResourceMapperXmlTest {
    @Test
    void resourceQueriesKeepTenantStoreAndVersionBoundaries() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/store/StoreResourceMapper.xml"), StandardCharsets.UTF_8);
        assertThat(xml).contains("tenant_id=#{tenantId}", "store_id=#{storeId}", "version=#{version}",
                "logistics_channel", "status='ACTIVE'");
        String migration = Files.readString(Path.of("../database/migrations/V020__add_merchant_store_resource_model.sql"), StandardCharsets.UTF_8);
        assertThat(migration).contains("uk_store_default_address", "uk_store_default_channel", "tenant_id, store_id");
        assertThat(StoreResourceMapper.class.getDeclaredMethod("findDefaultAddress", Long.class, Long.class)
                .getParameters()).extracting(p -> p.getAnnotation(Param.class).value())
                .containsExactly("tenantId", "storeId");
        assertThat(StoreResourceMapper.class.getDeclaredMethod("updateChannelDefault", Long.class, Long.class, Long.class, Long.class, Long.class)
                .getParameters()).extracting(p -> p.getAnnotation(Param.class).value())
                .containsExactly("tenantId", "storeId", "channelId", "version", "operatorId");
    }
}
