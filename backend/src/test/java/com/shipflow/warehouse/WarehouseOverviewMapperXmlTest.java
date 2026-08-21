package com.shipflow.warehouse;

import com.shipflow.warehouse.domain.WarehouseOverviewCounts;
import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.lang.reflect.Constructor;

import static org.assertj.core.api.Assertions.assertThat;

class WarehouseOverviewMapperXmlTest {
    @Test
    void usesTrackingEventsAndExplicitTenantScopedColumns() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/warehouse/WarehouseOverviewMapper.xml"));
        String countsMap = xml.substring(xml.indexOf("<resultMap id=\"overviewCounts\""),
                xml.indexOf("</resultMap>", xml.indexOf("<resultMap id=\"overviewCounts\"")));
        assertThat(xml)
                .contains("type=\"com.shipflow.warehouse.domain.WarehouseOverviewCounts\"",
                        "property=\"pendingInbound\" column=\"pending_inbound\" javaType=\"java.lang.Long\"",
                        "property=\"pendingMeasurement\" column=\"pending_measurement\" javaType=\"java.lang.Long\"",
                        "property=\"pendingLabel\" column=\"pending_label\" javaType=\"java.lang.Long\"",
                        "property=\"pendingHandover\" column=\"pending_handover\" javaType=\"java.lang.Long\"",
                        "property=\"pendingOutbound\" column=\"pending_outbound\" javaType=\"java.lang.Long\"",
                        "property=\"inTransit\" column=\"in_transit\" javaType=\"java.lang.Long\"",
                        "property=\"trackingExceptions\" column=\"tracking_exceptions\" javaType=\"java.lang.Long\"",
                        "property=\"todayInbound\" column=\"today_inbound\" javaType=\"java.lang.Long\"",
                        "property=\"todayOutbound\" column=\"today_outbound\" javaType=\"java.lang.Long\"",
                        "CAST(COALESCE(SUM", "CAST(COALESCE((SELECT COUNT(*)",
                        "tracking_event", "t.tenant_id=#{tenantId}", "process_status IN ('RETRY','REJECTED')",
                         "event_id AS exception_no", "COALESCE(t.event_description,t.process_message,'')",
                         "provider_order", "LEFT JOIN provider_order label_provider", "COALESCE(label_provider.lifecycle_status, '') &lt;&gt; 'LABEL_READY'",
                         "lifecycle_status", "destination_country", "chargeable_weight",
                         "tracking_no", "AS action", "UTC_DATE()")
                .doesNotContain("SELECT *", "FROM exception_case");
        assertThat(xml).doesNotContain("label_provider.lifecycle_status='CREATED'");
        assertThat(countsMap).doesNotContain("<constructor>");
    }

    @Test
    void countsProjectionHasPublicNoArgConstructorAndLongProperties() throws Exception {
        Constructor<WarehouseOverviewCounts> constructor = WarehouseOverviewCounts.class.getDeclaredConstructor();
        assertThat(constructor.canAccess(null)).isTrue();
        WarehouseOverviewCounts counts = constructor.newInstance();
        counts.setPendingInbound(0L);
        counts.setPendingMeasurement(0L);
        counts.setPendingLabel(0L);
        counts.setPendingHandover(0L);
        counts.setPendingOutbound(0L);
        counts.setInTransit(0L);
        counts.setTrackingExceptions(0L);
        counts.setTodayInbound(0L);
        counts.setTodayOutbound(0L);
        assertThat(counts.getPendingInbound()).isZero();
        assertThat(counts.getTodayOutbound()).isZero();
    }
}
