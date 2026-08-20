package com.shipflow.tracking;

import com.shipflow.sf.config.SfProperties;
import com.shipflow.tracking.mapper.ShipmentTrackingMapper;
import com.shipflow.tracking.scheduler.SfTrackingSimulationScheduler;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SfTrackingSimulationSchedulerTest {
    @Test
    void sandboxGateStopsAllDatabaseWorkWhenDisabled() {
        ShipmentTrackingMapper mapper = mock(ShipmentTrackingMapper.class);
        SfProperties properties = new SfProperties();
        properties.setSandboxEnabled(false);

        new SfTrackingSimulationScheduler(mapper, properties, Clock.systemUTC()).progressSandboxOrders();

        verifyNoInteractions(mapper);
    }

    @Test
    void advancesOneStageAndUpdatesOutboundToInTransit() {
        ShipmentTrackingMapper mapper = mock(ShipmentTrackingMapper.class);
        SfProperties properties = new SfProperties();
        properties.setSandboxEnabled(true);
        when(mapper.findSandboxOrders()).thenReturn(List.of(new ShipmentTrackingMapper.SandboxOrder(
                7L, 9L, "UAT-SF-1", "SF-1", "OUTBOUND", 3L, "Los Angeles")));
        when(mapper.findLatestSimulationStatus(7L, 9L)).thenReturn(null);
        when(mapper.transitionOrder(7L, 9L, "OUTBOUND", "IN_TRANSIT", 3L)).thenReturn(1);

        new SfTrackingSimulationScheduler(mapper, properties,
                Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC)).progressSandboxOrders();

        verify(mapper).insertSimulationEvent(eq(7L), eq(9L), eq("UAT-SF-1"), eq("SF-1"),
                eq("SF_PICKED_UP"), anyString(), anyString(), eq("深圳集散中心"), any());
        verify(mapper).transitionOrder(7L, 9L, "OUTBOUND", "IN_TRANSIT", 3L);
    }

    @Test
    void stopsAfterDeliveredTerminalEvent() {
        ShipmentTrackingMapper mapper = mock(ShipmentTrackingMapper.class);
        SfProperties properties = new SfProperties();
        properties.setSandboxEnabled(true);
        when(mapper.findSandboxOrders()).thenReturn(List.of(new ShipmentTrackingMapper.SandboxOrder(
                7L, 9L, "UAT-SF-1", "SF-1", "IN_TRANSIT", 8L, "Los Angeles")));
        when(mapper.findLatestSimulationStatus(7L, 9L)).thenReturn("DELIVERED");

        new SfTrackingSimulationScheduler(mapper, properties, Clock.systemUTC()).progressSandboxOrders();

        verify(mapper, never()).insertSimulationEvent(anyLong(), anyLong(), anyString(), any(), anyString(),
                anyString(), anyString(), anyString(), any());
        verify(mapper, never()).transitionOrder(anyLong(), anyLong(), anyString(), anyString(), anyLong());
    }
}
