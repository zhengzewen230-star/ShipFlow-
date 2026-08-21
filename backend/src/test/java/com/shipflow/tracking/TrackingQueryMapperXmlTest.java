package com.shipflow.tracking;

import org.junit.jupiter.api.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingQueryMapperXmlTest {
    @Test
    void tenantScopedSortedQueriesExist() {
        try {
            String xml = Files.readString(Path.of("src/main/resources/mapper/tracking/TrackingQueryMapper.xml"));
            assertThat(xml).contains("tenant_id=#{tenantId}", "ORDER BY event_time ASC,id ASC",
                    "findLatest", "orderExists", "findOrderStoreId", "countEvents", "findEventPage",
                    "event_id", "received_time").doesNotContain("SELECT *");
        } catch (Exception exception) {
            throw new AssertionError(exception);
        }
    }
}
