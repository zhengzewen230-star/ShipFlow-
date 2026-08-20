package com.shipflow.store;

import com.shipflow.store.mapper.StoreScopeMapper;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class StoreScopeMapperXmlTest {
    @Test
    void priceConfirmationScopeQueriesBindTenantStoreUserAndRole() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/store/StoreScopeMapper.xml"), StandardCharsets.UTF_8);

        assertThat(StoreScopeMapper.class.getDeclaredMethod("canRequestPriceConfirmation", Long.class, Long.class, Long.class)
                .getParameters()).extracting(parameter -> parameter.getAnnotation(org.apache.ibatis.annotations.Param.class).value())
                .containsExactly("tenantId", "userId", "storeId");
        assertThat(StoreScopeMapper.class.getDeclaredMethod("canConfirmPrice", Long.class, Long.class, Long.class)
                .getParameters()).extracting(parameter -> parameter.getAnnotation(org.apache.ibatis.annotations.Param.class).value())
                .containsExactly("tenantId", "userId", "storeId");
        assertThat(xml).contains("id=\"canRequestPriceConfirmation\"", "id=\"canConfirmPrice\"",
                "#{tenantId}", "#{userId}", "#{storeId}", "sys_user_store_scope",
                "MERCHANT_OPERATOR", "MERCHANT_ADMIN", "FINANCE_OPERATOR");
        assertThat(StoreScopeMapper.class.getDeclaredMethod("canManageStoreConfiguration", Long.class, Long.class, Long.class)
                .getParameters()).extracting(parameter -> parameter.getAnnotation(org.apache.ibatis.annotations.Param.class).value())
                .containsExactly("tenantId", "userId", "storeId");
        assertThat(xml).contains("id=\"canManageStoreConfiguration\"", "r.role_code='MERCHANT_ADMIN'");
        assertThat(StoreScopeMapper.class.getDeclaredMethod("canManageStoreStatus", Long.class, Long.class, Long.class)
                .getParameters()).extracting(parameter -> parameter.getAnnotation(org.apache.ibatis.annotations.Param.class).value())
                .containsExactly("tenantId", "userId", "storeId");
        assertThat(xml).contains("id=\"canManageStoreStatus\"", "s.status IN ('ACTIVE', 'DISABLED')");
    }
}
