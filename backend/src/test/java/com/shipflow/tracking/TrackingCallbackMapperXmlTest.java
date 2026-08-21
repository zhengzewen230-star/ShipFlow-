package com.shipflow.tracking;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingCallbackMapperXmlTest {
    @Test
    void mapperEnforcesIdentityIdempotencyTenantAndAppendOnlyFinancialBoundaries() throws Exception {
        String xml = Files.readString(Path.of("src/main/resources/mapper/tracking/TrackingCallbackMapper.xml"));

        assertThat(xml)
                .contains("p.permission_code = 'tracking:callback'")
                .contains("lp.provider_code = #{providerCode}")
                .contains("w.provider_id = #{providerId} AND w.tracking_no = #{trackingNo}")
                .contains("FOR UPDATE")
                .contains("provider_id = #{providerId} AND tracking_no = #{trackingNo} AND event_id = #{eventId}")
                .contains("INSERT INTO tracking_event")
                .contains("#{eventTime}", "#{receivedTime}", "CAST(#{rawPayload} AS JSON)", "#{processStatus}")
                .contains("WHERE tenant_id = #{tenantId} AND id = #{orderId} AND current_status = #{fromStatus}")
                .contains("INSERT INTO audit_log")
                .doesNotContain("SELECT *", "UPDATE shipment_quote_snapshot", "UPDATE fee_adjustment",
                        "UPDATE warehouse_measurement", "estimated_fee =", "current_fee =");
    }
}
