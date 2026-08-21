package com.shipflow.operations;

import com.shipflow.operations.application.OperationsQueryApplicationService;
import com.shipflow.operations.domain.OperationsWorkbenchQuery;
import com.shipflow.operations.domain.WorkbenchCounts;
import com.shipflow.operations.domain.WorkbenchRecentOrder;
import com.shipflow.operations.domain.WorkbenchRisk;
import com.shipflow.operations.mapper.OperationsMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class OperationsQueryApplicationServiceTest {
    private static final Instant NOW = Instant.parse("2026-08-17T02:30:00Z");

    @Test
    void buildsShanghaiWindowAndFinanceBreakdownFromOneTenantStoreScopedSnapshot() {
        OperationsMapper mapper = mock(OperationsMapper.class);
        when(mapper.findVisibleStoreIds(7L, 2L, null)).thenReturn(List.of(11L, 12L));
        when(mapper.findWorkbenchCounts(eq(7L), eq(2L), any(), any(), isNull())).thenReturn(new WorkbenchCounts(
                10, 2, 3, 4, 5, 6, 7,
                1, 2, 3, 4,
                5, 6, 7, 8, 9));
        when(mapper.findRecentOrders(eq(7L), eq(2L), any(), any(), isNull(), eq("updatedAt"), eq("DESC"), eq(0), eq(10)))
                .thenReturn(List.of(new WorkbenchRecentOrder(31L, "SO-31", 11L, "店铺一", "US",
                        "IN_TRANSIT", "LABEL_READY", new BigDecimal("5.400"), new BigDecimal("99.00"),
                        "USD", "SF-31", LocalDateTime.of(2026, 8, 17, 2, 0))));
        when(mapper.findRisks(eq(7L), eq(2L), any(), any(), isNull(), any(), anyInt(), anyInt()))
                .thenReturn(List.of(new WorkbenchRisk("RISK-1", "ORDER_UNPROCESSED", "MEDIUM", "订单长时间未处理",
                        "SHIPMENT_ORDER", 31L, 11L, LocalDateTime.of(2026, 8, 17, 2, 0), "超过平台默认 24 小时阈值")));

        OperationsQueryApplicationService service = new OperationsQueryApplicationService(
                mapper, Clock.fixed(NOW, ZoneOffset.UTC));

        var result = service.workbench(7L, 2L, OperationsWorkbenchQuery.defaults());

        assertThat(result.businessTimeZone()).isEqualTo("Asia/Shanghai");
        assertThat(result.scope().storeIds()).containsExactly(11L, 12L);
        assertThat(result.timeRange().from()).isEqualTo(Instant.parse("2026-08-16T16:00:00Z").atOffset(ZoneOffset.UTC));
        assertThat(result.timeRange().to()).isEqualTo(Instant.parse("2026-08-17T16:00:00Z").atOffset(ZoneOffset.UTC));
        assertThat(result.metrics()).extracting(metric -> metric.key())
                .containsExactly("PENDING_ORDERS", "PENDING_INBOUND", "PENDING_MEASUREMENT", "PENDING_LABEL",
                        "PENDING_OUTBOUND", "IN_TRANSIT", "TRACKING_EXCEPTION", "PENDING_FINANCE");
        var finance = result.metrics().get(7);
        assertThat(finance.count()).isEqualTo(10);
        assertThat(finance.breakdown()).extracting(item -> item.key())
                .containsExactly("PENDING_FEE_CONFIRMATION", "BILL_IMPORT_ERRORS", "RECONCILIATION_DIFFERENCE", "PENDING_FINANCE_REVIEW");
        assertThat(finance.breakdown()).extracting(item -> item.count()).containsExactly(1L, 2L, 3L, 4L);
        assertThat(result.recentOrders()).hasSize(1);
        assertThat(result.recentOrders().get(0).storeId()).isEqualTo(11L);
        assertThat(result.risks().get(0).description()).contains("24 小时");
        verify(mapper).findVisibleStoreIds(7L, 2L, null);
    }

    @Test
    void rejectsUnauthorizedRequestedStoreBeforeBusinessQueries() {
        OperationsMapper mapper = mock(OperationsMapper.class);
        when(mapper.findVisibleStoreIds(7L, 2L, 99L)).thenReturn(List.of());
        OperationsQueryApplicationService service = new OperationsQueryApplicationService(
                mapper, Clock.fixed(NOW, ZoneOffset.UTC));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> service.workbench(7L, 2L,
                        new OperationsWorkbenchQuery("TODAY", null, null, 99L, 1, 20, "updatedAt", "DESC", null, 10)))
                .isInstanceOf(com.shipflow.order.application.ShipmentOrderException.class)
                .extracting(error -> ((com.shipflow.order.application.ShipmentOrderException) error).code())
                .isEqualTo("COMMON-1004");
        verify(mapper, never()).findWorkbenchCounts(anyLong(), anyLong(), any(), any(), any());
    }

    @Test
    void returnsEmptySnapshotWhenReadMappersReturnNull() {
        OperationsMapper mapper = mock(OperationsMapper.class);
        when(mapper.findVisibleStoreIds(7L, 2L, null)).thenReturn(null);
        when(mapper.findWorkbenchCounts(eq(7L), eq(2L), any(), any(), isNull())).thenReturn(null);
        when(mapper.findRecentOrders(eq(7L), eq(2L), any(), any(), isNull(), eq("updatedAt"), eq("DESC"), eq(0), eq(10)))
                .thenReturn(null);
        when(mapper.findRisks(eq(7L), eq(2L), any(), any(), isNull(), any(), anyInt(), anyInt()))
                .thenReturn(null);

        var result = new OperationsQueryApplicationService(mapper, Clock.fixed(NOW, ZoneOffset.UTC))
                .workbench(7L, 2L, OperationsWorkbenchQuery.defaults());

        assertThat(result.scope().storeIds()).isEmpty();
        assertThat(result.metrics()).hasSize(8).allSatisfy(metric -> assertThat(metric.count()).isZero());
        assertThat(result.todos()).hasSize(6).allSatisfy(todo -> assertThat(todo.count()).isZero());
        assertThat(result.recentOrders()).isEmpty();
        assertThat(result.risks()).isEmpty();
    }
}
