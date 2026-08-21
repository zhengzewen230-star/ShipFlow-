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
                Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC), () -> 0.30d).progressSandboxOrders();

        verify(mapper).insertSimulationEvent(eq(7L), eq(9L), eq("UAT-SF-1"), eq("SF-1"),
                eq("SF_PICKED_UP"), anyString(), anyString(), eq("深圳集散中心"), any());
        verify(mapper).transitionOrder(7L, 9L, "OUTBOUND", "IN_TRANSIT", 3L);
    }

    @Test
    void createsOneOpenTransportExceptionAndStopsNormalProgressionWhenDrawIsBelowThirtyPercent() {
        ShipmentTrackingMapper mapper = mock(ShipmentTrackingMapper.class);
        SfProperties properties = new SfProperties();
        properties.setSandboxEnabled(true);
        when(mapper.findSandboxOrders()).thenReturn(List.of(new ShipmentTrackingMapper.SandboxOrder(
                7L, 9L, "UAT-SF-1", "SF-1", "OUTBOUND", 3L, "Los Angeles")));
        when(mapper.findLatestSimulationStatus(7L, 9L)).thenReturn(null);
        when(mapper.insertSimulationTransportException(eq(7L), eq(9L), eq("SIMEX-9"), anyString(), any()))
                .thenReturn(1);

        new SfTrackingSimulationScheduler(mapper, properties,
                Clock.fixed(Instant.parse("2026-08-16T00:00:00Z"), ZoneOffset.UTC), () -> 0.29d).progressSandboxOrders();

        verify(mapper).insertSimulationTransportException(eq(7L), eq(9L), eq("SIMEX-9"),
                contains("运输异常"), eq(java.time.LocalDateTime.of(2026, 8, 16, 0, 0)));
        verify(mapper).insertSimulationExceptionAudit(eq(7L), eq("SIMEX-9"),
                contains("运输异常"), eq(java.time.LocalDateTime.of(2026, 8, 16, 0, 0)));
        verify(mapper).insertSimulationEvent(eq(7L), eq(9L), eq("UAT-SF-1"), eq("SF-1"),
                eq("TRANSPORT_EXCEPTION"), contains("运输异常"), contains("运输异常"), eq("深圳集散中心"), any());
        verify(mapper, never()).transitionOrder(anyLong(), anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void doesNotWriteAnEventOrAuditWhenTheDatabaseRejectsADuplicateSimulationException() {
        ShipmentTrackingMapper mapper = mock(ShipmentTrackingMapper.class);
        SfProperties properties = new SfProperties();
        properties.setSandboxEnabled(true);
        when(mapper.findSandboxOrders()).thenReturn(List.of(new ShipmentTrackingMapper.SandboxOrder(
                7L, 9L, "UAT-SF-1", "SF-1", "OUTBOUND", 3L, "Los Angeles")));
        when(mapper.findLatestSimulationStatus(7L, 9L)).thenReturn(null);
        when(mapper.insertSimulationTransportException(anyLong(), anyLong(), anyString(), anyString(), any())).thenReturn(0);

        new SfTrackingSimulationScheduler(mapper, properties, Clock.systemUTC(), () -> 0.0d).progressSandboxOrders();

        verify(mapper, never()).insertSimulationExceptionAudit(anyLong(), anyString(), anyString(), any());
        verify(mapper, never()).insertSimulationEvent(anyLong(), anyLong(), anyString(), any(), anyString(),
                anyString(), anyString(), anyString(), any());
        verify(mapper, never()).transitionOrder(anyLong(), anyLong(), anyString(), anyString(), anyLong());
    }

    @Test
    void doesNotRepeatOrAdvanceAfterASimulatedTransportException() {
        ShipmentTrackingMapper mapper = mock(ShipmentTrackingMapper.class);
        SfProperties properties = new SfProperties();
        properties.setSandboxEnabled(true);
        when(mapper.findSandboxOrders()).thenReturn(List.of(new ShipmentTrackingMapper.SandboxOrder(
                7L, 9L, "UAT-SF-1", "SF-1", "IN_TRANSIT", 3L, "Los Angeles")));
        when(mapper.findLatestSimulationStatus(7L, 9L)).thenReturn("TRANSPORT_EXCEPTION");

        new SfTrackingSimulationScheduler(mapper, properties, Clock.systemUTC(), () -> 0.0d).progressSandboxOrders();

        verify(mapper, never()).insertSimulationTransportException(anyLong(), anyLong(), anyString(), anyString(), any());
        verify(mapper, never()).insertSimulationExceptionAudit(anyLong(), anyString(), anyString(), any());
        verify(mapper, never()).insertSimulationEvent(anyLong(), anyLong(), anyString(), any(), anyString(),
                anyString(), anyString(), anyString(), any());
        verify(mapper, never()).transitionOrder(anyLong(), anyLong(), anyString(), anyString(), anyLong());
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
