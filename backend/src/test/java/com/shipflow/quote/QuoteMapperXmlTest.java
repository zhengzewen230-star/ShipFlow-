package com.shipflow.quote;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class QuoteMapperXmlTest {
    @Test
    void queryStatementsUseExplicitColumnsAndTenantPredicates() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/quote/QuoteMapper.xml"));

        assertThat(xml)
                .contains("id=#{quoteId} AND tenant_id=#{tenantId}")
                .contains("tenant_id=#{tenantId}")
                .contains("shipment_order WHERE tenant_id=#{tenantId} AND quote_id=#{quoteId}")
                .contains("ORDER BY created_at DESC, id DESC")
                .doesNotContain("SELECT *");
    }

    @Test
    void mapperXmlParsesWithMyBatisBuilder() throws Exception {
        var configuration = new org.apache.ibatis.session.Configuration();
        try (var input = Files.newInputStream(Path.of("src/main/resources/mapper/quote/QuoteMapper.xml"))) {
            new org.apache.ibatis.builder.xml.XMLMapperBuilder(input, configuration,
                    "mapper/quote/QuoteMapper.xml", configuration.getSqlFragments()).parse();
        }
        assertThat(configuration.hasStatement("com.shipflow.quote.mapper.QuoteMapper.findPage")).isTrue();
        assertThat(configuration.hasStatement("com.shipflow.quote.mapper.QuoteMapper.hasShipmentOrder")).isTrue();
    }

    @Test
    void quoteCreationMapperXmlsHaveScopedIdempotencyAndCurrentRuleQuery() throws Exception {
        String idempotencyXml = Files.readString(Path.of("src/main/resources/mapper/quote/QuoteIdempotencyMapper.xml"));
        String pricingXml = Files.readString(Path.of("src/main/resources/mapper/quote/QuotePricingMapper.xml"));
        assertThat(idempotencyXml).contains("scope_tenant_id=#{tenantId}", "'SUCCEEDED'", "resource_type='QUOTE'");
        assertThat(pricingXml).contains("status='ACTIVE'", "country_code=#{countryCode}", "status='PUBLISHED'", "effective_to")
                .doesNotContain("SELECT *");

        var configuration = new org.apache.ibatis.session.Configuration();
        for (String file : java.util.List.of("QuoteIdempotencyMapper.xml", "QuotePricingMapper.xml", "QuoteAuditMapper.xml")) {
            try (var input = Files.newInputStream(Path.of("src/main/resources/mapper/quote", file))) {
                new org.apache.ibatis.builder.xml.XMLMapperBuilder(input, configuration, file, configuration.getSqlFragments()).parse();
            }
        }
        assertThat(configuration.hasStatement("com.shipflow.quote.mapper.QuotePricingMapper.findCurrentRule")).isTrue();
        assertThat(configuration.hasStatement("com.shipflow.quote.mapper.QuoteIdempotencyMapper.complete")).isTrue();
    }
}
