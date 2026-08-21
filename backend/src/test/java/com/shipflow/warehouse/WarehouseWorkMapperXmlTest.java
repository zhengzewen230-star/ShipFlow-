package com.shipflow.warehouse;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseWorkMapperXmlTest {
    @Test
    void usesExplicitTenantScopedProjectionAndNoSelectStar() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/warehouse/WarehouseWorkMapper.xml"), StandardCharsets.UTF_8);
        assertThat(xml).contains("namespace=\"com.shipflow.warehouse.mapper.WarehouseWorkMapper\"",
                "o.tenant_id=#{tenantId}", "o.order_no AS business_order_no", "actual_volume_weight",
                "fee_difference", "warehouse_status", "LIMIT #{offset},#{pageSize}",
                "<sql id=\"fromWhereById\">", "<include refid=\"fromWhereById\"/>")
                .doesNotContain("SELECT *");
    }
}
