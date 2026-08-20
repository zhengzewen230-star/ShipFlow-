package com.shipflow.warehouse;import com.shipflow.warehouse.application.*;import com.shipflow.warehouse.mapper.*;import com.shipflow.order.domain.model.ShipmentOrder;import com.shipflow.warehouse.api.model.*;import org.junit.jupiter.api.Test;import java.math.*;import java.nio.charset.StandardCharsets;import java.security.MessageDigest;import java.time.*;import java.util.HexFormat;import static org.assertj.core.api.Assertions.*;import static org.mockito.Mockito.*;
class WarehouseApplicationServiceTest{
    private static ShipmentOrder order(String status, long version) {
        return new ShipmentOrder(1L, 7L, "O", "k", 1L, 1L, 1L, status, "CN", "US",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.ONE, "USD", version, LocalDateTime.now());
    }

    @Test void rejectsInboundOutsidePendingInbound() {
        WarehouseMapper m = mock(WarehouseMapper.class);
        when(m.findOrder(7L, 1L)).thenReturn(order("DRAFT", 0L));
        var s = new WarehouseApplicationService(m, Clock.systemUTC());
        assertThatThrownBy(() -> s.inbound(7L, 2L, 1L, new InboundRequest(0L), null))
                .isInstanceOf(WarehouseException.class)
                .satisfies(e -> assertThat(((WarehouseException) e).code()).isEqualTo("WAREHOUSE-1001"));
    }

    @Test void measurementReturnsTheNewVersion() {
        WarehouseMapper m = mock(WarehouseMapper.class);
        when(m.findOrder(7L, 1L)).thenReturn(order("INBOUND", 3L), order("READY_FOR_OUTBOUND", 4L));
        when(m.packageId(7L, 1L)).thenReturn(11L);
        when(m.updateFee(eq(7L), eq(1L), any(), any(), eq("READY_FOR_OUTBOUND"), eq(3L))).thenReturn(1);
        var result = new WarehouseApplicationService(m, Clock.systemUTC()).measure(7L, 2L, 1L,
                new MeasurementRequest(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 3L), "req");
        assertThat(result.version()).isEqualTo(4L);
        verify(m).updateFee(eq(7L), eq(1L), any(), any(), eq("READY_FOR_OUTBOUND"), eq(3L));
    }

    @Test void staleMeasurementVersionRollsBackWithConflict() {
        WarehouseMapper m = mock(WarehouseMapper.class);
        when(m.findOrder(7L, 1L)).thenReturn(order("INBOUND", 3L));
        when(m.packageId(7L, 1L)).thenReturn(11L);
        when(m.updateFee(eq(7L), eq(1L), any(), any(), any(), eq(2L))).thenReturn(0);
        assertThatThrownBy(() -> new WarehouseApplicationService(m, Clock.systemUTC()).measure(7L, 2L, 1L,
                new MeasurementRequest(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 2L), "req"))
                .isInstanceOf(WarehouseException.class)
                .satisfies(e -> assertThat(((WarehouseException) e).code()).isEqualTo("COMMON-1005"));
    }

    @Test void rejectsInvalidMeasurementBeforeWriting() {
        WarehouseMapper m = mock(WarehouseMapper.class);
        var service = new WarehouseApplicationService(m, Clock.systemUTC());

        assertThatThrownBy(() -> service.measure(7L, 2L, 1L,
                new MeasurementRequest(new BigDecimal("1.2345"), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 3L), "req"))
                .isInstanceOf(WarehouseException.class)
                .satisfies(e -> {
                    assertThat(((WarehouseException) e).code()).isEqualTo("WAREHOUSE-1002");
                    assertThat(((WarehouseException) e).status()).isEqualTo(422);
                });
        verifyNoInteractions(m);
    }

    @Test void rejectsMeasurementAboveMaximumBeforeWriting() {
        WarehouseMapper m = mock(WarehouseMapper.class);
        var service = new WarehouseApplicationService(m, Clock.systemUTC());

        assertThatThrownBy(() -> service.measure(7L, 2L, 1L,
                new MeasurementRequest(new BigDecimal("1000000.001"), BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 3L), "req"))
                .isInstanceOf(WarehouseException.class)
                .satisfies(e -> assertThat(((WarehouseException) e).code()).isEqualTo("WAREHOUSE-1002"));
        verifyNoInteractions(m);
    }

    @Test void rejectsCrossTenantOrderWithoutWriting() {
        WarehouseMapper m = mock(WarehouseMapper.class);
        when(m.findOrder(8L, 1L)).thenReturn(null);

        assertThatThrownBy(() -> new WarehouseApplicationService(m, Clock.systemUTC()).measure(8L, 2L, 1L,
                new MeasurementRequest(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 3L), "req"))
                .isInstanceOf(WarehouseException.class)
                .satisfies(e -> assertThat(((WarehouseException) e).code()).isEqualTo("COMMON-1006"));
        verify(m).findOrder(8L, 1L);
        verify(m, never()).insertMeasurement(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test void rejectsMeasurementWhenOrderIsNotInbound() {
        WarehouseMapper m = mock(WarehouseMapper.class);
        when(m.findOrder(7L, 1L)).thenReturn(order("PENDING_PRICE_CONFIRMATION", 3L));

        assertThatThrownBy(() -> new WarehouseApplicationService(m, Clock.systemUTC()).measure(7L, 2L, 1L,
                new MeasurementRequest(BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, 3L), "req"))
                .isInstanceOf(WarehouseException.class)
                .satisfies(e -> assertThat(((WarehouseException) e).code()).isEqualTo("WAREHOUSE-1002"));
        verify(m, never()).insertMeasurement(anyLong(), anyLong(), any(), any(), any(), any(), any(), any(), anyLong(), any());
    }

    @Test void outboundRequiresAChannelBoundRecord() {
        WarehouseMapper m = mock(WarehouseMapper.class);
        when(m.findOrder(7L, 1L)).thenReturn(order("READY_FOR_OUTBOUND", 5L));
        when(m.packageId(7L, 1L)).thenReturn(11L);
        when(m.outbound(eq(7L), eq(1L), eq(11L), eq("SF-TEST"), eq(2L), any(LocalDateTime.class),
                isNull(String.class), startsWith("WH-"), startsWith("MANIFEST-WH-"))).thenReturn(0);
        assertThatThrownBy(() -> new WarehouseApplicationService(m, Clock.systemUTC()).outbound(7L, 2L, 1L,
                new OutboundRequest(5L, "SF-TEST", null), "req"))
                .isInstanceOf(WarehouseException.class)
                .satisfies(e -> assertThat(((WarehouseException) e).code()).isEqualTo("WAREHOUSE-1003"));
    }

    @Test void sameIdempotencyKeyReplaysWithoutASecondTransition() {
        WarehouseMapper m = mock(WarehouseMapper.class);
        InboundRequest request = new InboundRequest(0L);
        String hash = HexFormat.of().formatHex(hashBytes("1\n" + request));
        when(m.findIdempotency(7L, "warehouseInbound", "same-key"))
                .thenReturn(null, new WarehouseMapper.IdempotencyRecord(hash, 1L));
        when(m.findOrder(7L, 1L)).thenReturn(order("PENDING_INBOUND", 0L), order("INBOUND", 1L), order("INBOUND", 1L));
        when(m.transition(7L, 1L, "PENDING_INBOUND", "INBOUND", 0L)).thenReturn(1);
        var s = new WarehouseApplicationService(m, Clock.systemUTC());
        s.inbound(7L, 2L, 1L, request, "same-key", "req-1");
        WarehouseResult replay = s.inbound(7L, 2L, 1L, request, "same-key", "req-2");
        assertThat(replay.version()).isEqualTo(1L);
        verify(m, times(1)).transition(7L, 1L, "PENDING_INBOUND", "INBOUND", 0L);
    }

    private static byte[] hashBytes(String value) {
        try { return MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8)); }
        catch (Exception exception) { throw new AssertionError(exception); }
    }
}
