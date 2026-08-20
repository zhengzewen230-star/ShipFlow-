package com.shipflow.order;

import com.shipflow.order.api.model.PriceConfirmationRequest;
import com.shipflow.order.api.model.PriceConfirmationView;
import com.shipflow.order.application.PriceConfirmationApplicationService;
import com.shipflow.order.application.ShipmentOrderException;
import com.shipflow.order.mapper.PriceConfirmationIdempotencyMapper;
import com.shipflow.order.mapper.PriceConfirmationMapper;
import com.shipflow.store.mapper.StoreScopeMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PriceConfirmationApplicationServiceTest {
    private static final Long TENANT_ID = 7L;
    private static final Long OPERATOR_ID = 11L;
    private static final Long FINANCE_ID = 12L;
    private static final Long ORDER_ID = 9L;
    private static final Long STORE_ID = 3L;
    private static final Long ADJUSTMENT_ID = 41L;
    private static final Clock CLOCK = Clock.fixed(Instant.parse("2026-08-17T00:00:00Z"), ZoneOffset.UTC);

    private final PriceConfirmationMapper mapper = mock(PriceConfirmationMapper.class);
    private final PriceConfirmationIdempotencyMapper idempotency = mock(PriceConfirmationIdempotencyMapper.class);
    private final StoreScopeMapper storeScope = mock(StoreScopeMapper.class);
    private final PriceConfirmationApplicationService service =
            new PriceConfirmationApplicationService(mapper, idempotency, storeScope, CLOCK);

    @Test
    void merchantOperatorCanSubmitOnlyForAnAuthorizedStoreAndOrderStaysPending() {
        PriceConfirmationMapper.OrderRecord order = order("PENDING_PRICE_CONFIRMATION", 4L, fee("12.50"));
        PriceConfirmationMapper.AdjustmentRecord pending = adjustment("PENDING_CONFIRMATION", null, null);
        PriceConfirmationMapper.AdjustmentRecord requested = adjustment("REQUESTED", LocalDateTime.of(2026, 8, 17, 0, 0), null);
        when(mapper.findOrder(TENANT_ID, ORDER_ID)).thenReturn(order);
        when(storeScope.canRequestPriceConfirmation(TENANT_ID, OPERATOR_ID, STORE_ID)).thenReturn(true);
        when(storeScope.canAccessStore(TENANT_ID, OPERATOR_ID, STORE_ID)).thenReturn(true);
        when(idempotency.find(eq(TENANT_ID), eq("submitPriceConfirmationRequest"), eq("request-1"))).thenReturn(null);
        when(mapper.findAdjustment(TENANT_ID, ORDER_ID, ADJUSTMENT_ID)).thenReturn(pending);
        when(mapper.markRequested(eq(TENANT_ID), eq(ORDER_ID), eq(ADJUSTMENT_ID), eq(OPERATOR_ID), any(LocalDateTime.class))).thenReturn(1);
        when(mapper.findLatestAdjustment(TENANT_ID, ORDER_ID)).thenReturn(requested);

        PriceConfirmationView result = service.request(TENANT_ID, OPERATOR_ID, ORDER_ID,
                request(4L), "request-1", "trace-1");

        assertThat(result.confirmationStatus()).isEqualTo("REQUESTED");
        assertThat(result.currentStatus()).isEqualTo("PENDING_PRICE_CONFIRMATION");
        verify(mapper, never()).confirmOrder(any(), any(), any(), any());
        verify(idempotency).complete(TENANT_ID, "submitPriceConfirmationRequest", "request-1", ORDER_ID);
    }

    @Test
    void merchantOperatorCannotPerformFinalConfirmation() {
        when(mapper.findOrder(TENANT_ID, ORDER_ID)).thenReturn(order("PENDING_PRICE_CONFIRMATION", 4L, fee("12.50")));
        when(storeScope.canConfirmPrice(TENANT_ID, OPERATOR_ID, STORE_ID)).thenReturn(false);

        assertThatThrownBy(() -> service.confirm(TENANT_ID, OPERATOR_ID, ORDER_ID,
                request(4L), "confirm-1", "trace-2"))
                .isInstanceOf(ShipmentOrderException.class)
                .satisfies(error -> assertThat(((ShipmentOrderException) error).code()).isEqualTo("COMMON-1004"));

        verify(idempotency, never()).find(any(), anyString(), anyString());
        verify(mapper, never()).confirmOrder(any(), any(), any(), any());
    }

    @Test
    void financeConfirmationAdvancesOrderAndAdjustmentAtomicallyByVersion() {
        PriceConfirmationMapper.OrderRecord pendingOrder = order("PENDING_PRICE_CONFIRMATION", 4L, fee("12.50"));
        PriceConfirmationMapper.OrderRecord readyOrder = order("READY_FOR_OUTBOUND", 5L, fee("12.50"));
        PriceConfirmationMapper.AdjustmentRecord requested = adjustment("REQUESTED", LocalDateTime.of(2026, 8, 17, 0, 0), null);
        PriceConfirmationMapper.AdjustmentRecord confirmed = adjustment("CONFIRMED", requested.requestedAt(), LocalDateTime.of(2026, 8, 17, 0, 0));
        when(mapper.findOrder(TENANT_ID, ORDER_ID)).thenReturn(pendingOrder, readyOrder);
        when(storeScope.canConfirmPrice(TENANT_ID, FINANCE_ID, STORE_ID)).thenReturn(true);
        when(storeScope.canAccessStore(TENANT_ID, FINANCE_ID, STORE_ID)).thenReturn(true);
        when(idempotency.find(eq(TENANT_ID), eq("confirmPrice"), eq("confirm-1"))).thenReturn(null);
        when(mapper.findAdjustment(TENANT_ID, ORDER_ID, ADJUSTMENT_ID)).thenReturn(requested);
        when(mapper.confirmOrder(TENANT_ID, ORDER_ID, fee("12.50"), 4L)).thenReturn(1);
        when(mapper.confirmAdjustment(eq(TENANT_ID), eq(ORDER_ID), eq(ADJUSTMENT_ID), eq(fee("12.50")), eq(FINANCE_ID), any(LocalDateTime.class))).thenReturn(1);
        when(mapper.findLatestAdjustment(TENANT_ID, ORDER_ID)).thenReturn(confirmed);

        PriceConfirmationView result = service.confirm(TENANT_ID, FINANCE_ID, ORDER_ID,
                request(4L), "confirm-1", "trace-3");

        assertThat(result.currentStatus()).isEqualTo("READY_FOR_OUTBOUND");
        assertThat(result.version()).isEqualTo(5L);
        assertThat(result.confirmationStatus()).isEqualTo("CONFIRMED");
        assertThat(result.confirmedAt()).isEqualTo(java.time.OffsetDateTime.parse("2026-08-17T00:00Z"));
        verify(mapper).confirmOrder(TENANT_ID, ORDER_ID, fee("12.50"), 4L);
        verify(mapper).confirmAdjustment(eq(TENANT_ID), eq(ORDER_ID), eq(ADJUSTMENT_ID), eq(fee("12.50")), eq(FINANCE_ID), any(LocalDateTime.class));
        verify(idempotency).complete(TENANT_ID, "confirmPrice", "confirm-1", ORDER_ID);
    }

    @Test
    void rejectsCrossTenantOrUnownedOrderBeforeChangingAnything() {
        when(mapper.findOrder(TENANT_ID, ORDER_ID)).thenReturn(null);

        assertThatThrownBy(() -> service.get(TENANT_ID, OPERATOR_ID, ORDER_ID))
                .isInstanceOf(ShipmentOrderException.class)
                .satisfies(error -> assertThat(((ShipmentOrderException) error).code()).isEqualTo("COMMON-1006"));

        verify(storeScope, never()).canAccessStore(any(), any(), any());
        verify(mapper, never()).markRequested(any(), any(), any(), any(), any());
    }

    @Test
    void rejectsMismatchedExpectedFeeAndStaleVersion() {
        PriceConfirmationMapper.OrderRecord order = order("PENDING_PRICE_CONFIRMATION", 4L, fee("12.50"));
        PriceConfirmationMapper.AdjustmentRecord adjustment = adjustment("REQUESTED", null, null);
        when(mapper.findOrder(TENANT_ID, ORDER_ID)).thenReturn(order);
        when(storeScope.canConfirmPrice(TENANT_ID, FINANCE_ID, STORE_ID)).thenReturn(true);
        when(idempotency.find(eq(TENANT_ID), eq("confirmPrice"), eq("confirm-2"))).thenReturn(null);
        when(mapper.findAdjustment(TENANT_ID, ORDER_ID, ADJUSTMENT_ID)).thenReturn(adjustment);

        assertThatThrownBy(() -> service.confirm(TENANT_ID, FINANCE_ID, ORDER_ID,
                new PriceConfirmationRequest(ADJUSTMENT_ID, fee("99.00"), 4L), "confirm-2", null))
                .isInstanceOf(ShipmentOrderException.class)
                .satisfies(error -> assertThat(((ShipmentOrderException) error).code()).isEqualTo("ORDER-1011"));
        verify(mapper, never()).confirmOrder(any(), any(), any(), any());

        when(mapper.findAdjustment(TENANT_ID, ORDER_ID, ADJUSTMENT_ID)).thenReturn(adjustment(ADJUSTMENT_ID, "REQUESTED", fee("12.50"), null, null));
        when(mapper.confirmOrder(TENANT_ID, ORDER_ID, fee("12.50"), 4L)).thenReturn(0);
        assertThatThrownBy(() -> service.confirm(TENANT_ID, FINANCE_ID, ORDER_ID,
                request(4L), "confirm-3", null))
                .isInstanceOf(ShipmentOrderException.class)
                .satisfies(error -> assertThat(((ShipmentOrderException) error).code()).isEqualTo("COMMON-1005"));
        verify(mapper, never()).confirmAdjustment(any(), any(), any(), any(), any(), any());
    }

    private PriceConfirmationRequest request(long version) {
        return new PriceConfirmationRequest(ADJUSTMENT_ID, fee("12.50"), version);
    }

    private PriceConfirmationMapper.OrderRecord order(String status, long version, BigDecimal currentFee) {
        return new PriceConfirmationMapper.OrderRecord(ORDER_ID, TENANT_ID, "SO-9", STORE_ID, 8L,
                status, fee("10.00"), currentFee, null, "CNY", fee("2.000"), version,
                LocalDateTime.of(2026, 8, 16, 0, 0));
    }

    private PriceConfirmationMapper.AdjustmentRecord adjustment(String status, LocalDateTime requestedAt,
                                                                  LocalDateTime confirmedAt) {
        return adjustment(ADJUSTMENT_ID, status, fee("12.50"), requestedAt, confirmedAt);
    }

    private PriceConfirmationMapper.AdjustmentRecord adjustment(Long id, String status, BigDecimal afterAmount,
                                                                  LocalDateTime requestedAt, LocalDateTime confirmedAt) {
        return new PriceConfirmationMapper.AdjustmentRecord(id, ORDER_ID, STORE_ID, "INCREASE",
                fee("10.00"), afterAmount, fee("2.50"), "CNY", status, requestedAt, confirmedAt);
    }

    private BigDecimal fee(String value) {
        return new BigDecimal(value);
    }
}
