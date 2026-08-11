package com.shipflow.tracking;

import com.shipflow.tracking.domain.TrackingStatusMapping;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class TrackingStatusMappingTest {
    @Test
    void mapsProviderCodesToCanonicalOrderStatuses() {
        assertThat(TrackingStatusMapping.targetStatus("PICKED_UP")).contains("IN_TRANSIT");
        assertThat(TrackingStatusMapping.targetStatus("departed")).contains("IN_TRANSIT");
        assertThat(TrackingStatusMapping.targetStatus("DELIVERED")).contains("DELIVERED");
        assertThat(TrackingStatusMapping.targetStatus("UNKNOWN")).isEmpty();
    }

    @Test
    void onlyAdvancesAlongExistingStateMachine() {
        assertThat(TrackingStatusMapping.decide("OUTBOUND", "IN_TRANSIT"))
                .isEqualTo(TrackingStatusMapping.Decision.ADVANCE);
        assertThat(TrackingStatusMapping.decide("IN_TRANSIT", "DELIVERED"))
                .isEqualTo(TrackingStatusMapping.Decision.ADVANCE);
        assertThat(TrackingStatusMapping.decide("OUTBOUND", "DELIVERED"))
                .isEqualTo(TrackingStatusMapping.Decision.ILLEGAL);
    }

    @Test
    void terminalAndSameStatusEventsAreRecordedWithoutRollback() {
        assertThat(TrackingStatusMapping.decide("DELIVERED", "IN_TRANSIT"))
                .isEqualTo(TrackingStatusMapping.Decision.RECORDED_ONLY);
        assertThat(TrackingStatusMapping.decide("IN_TRANSIT", "IN_TRANSIT"))
                .isEqualTo(TrackingStatusMapping.Decision.RECORDED_ONLY);
        assertThat(TrackingStatusMapping.decide("RETURNED", "DELIVERED"))
                .isEqualTo(TrackingStatusMapping.Decision.RECORDED_ONLY);
    }
}
