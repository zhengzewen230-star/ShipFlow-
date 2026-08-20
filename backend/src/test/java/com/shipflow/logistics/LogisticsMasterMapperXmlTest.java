package com.shipflow.logistics;
import org.junit.jupiter.api.Test; import java.nio.file.*; import static org.assertj.core.api.Assertions.*;
class LogisticsMasterMapperXmlTest {
    @Test
    void masterDataQueriesUseExplicitColumnsAndOptimisticLock() {
        try {
            String xml = Files.readString(Path.of("src/main/resources/mapper/logistics/LogisticsMasterMapper.xml"));
            assertThat(xml)
                    .contains("deleted=0", "version=version+1", "version=#{version}", "INSERT INTO price_rule",
                            "p.status='ACTIVE'", "status='PUBLISHED'")
                    .doesNotContain("SELECT *");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void publicDetailQueryDoesNotReuseListOnlyFilterParameters() {
        try {
            String xml = Files.readString(Path.of("src/main/resources/mapper/logistics/LogisticsMasterMapper.xml"));
            int detailStart = xml.indexOf("<select id=\"findPublicChannel\"");
            int detailEnd = xml.indexOf("</select>", detailStart);
            assertThat(detailStart).isGreaterThanOrEqualTo(0);
            assertThat(detailEnd).isGreaterThan(detailStart);
            String detailQuery = xml.substring(detailStart, detailEnd);
            assertThat(detailQuery)
                    .contains("c.id=#{channelId}", "c.deleted=0", "p.deleted=0", "p.status='ACTIVE'")
                    .doesNotContain("publicChannelFilters", "channelCode", "channelName", "serviceCountry", "status=#{status}");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }

    @Test
    void idempotencyRecordConstructorUsesConcreteTypes() {
        try {
            String xml = Files.readString(Path.of("src/main/resources/mapper/logistics/LogisticsIdempotencyMapper.xml"));
            assertThat(xml).contains("java.lang.String", "java.lang.Long", "scope_tenant_id=0")
                    .doesNotContain("javaType=\"Object\"");
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
