package com.shipflow.warehouse;

import com.shipflow.warehouse.application.WarehouseOverviewApplicationService;
import com.shipflow.warehouse.domain.WarehouseOverview;
import com.shipflow.warehouse.domain.WarehouseOverviewCounts;
import com.shipflow.warehouse.mapper.WarehouseOverviewMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WarehouseOverviewApplicationServiceTest {
    @Test
    void combinesCountsAndRecentTenantScopedRows() {
        WarehouseOverviewMapper mapper = mock(WarehouseOverviewMapper.class);
        when(mapper.findOverviewCounts(7L)).thenReturn(counts(1L, 2L, 3L, 4L, 4L, 5L, 6L, 7L, 8L));
        when(mapper.findRecentOrders(7L, 10)).thenReturn(List.of());
        when(mapper.findRecentTrackingExceptions(7L, 10)).thenReturn(List.of());

        WarehouseOverview result = new WarehouseOverviewApplicationService(mapper).overview(7L);

        assertThat(result.pendingInbound()).isEqualTo(1);
        assertThat(result.pendingMeasurement()).isEqualTo(2);
        assertThat(result.pendingLabel()).isEqualTo(3);
        assertThat(result.pendingHandover()).isEqualTo(4);
        assertThat(result.pendingOutbound()).isEqualTo(4);
        assertThat(result.inTransit()).isEqualTo(5);
        assertThat(result.trackingExceptions()).isEqualTo(6);
        assertThat(result.todayInbound()).isEqualTo(7);
        assertThat(result.todayOutbound()).isEqualTo(8);
        verify(mapper).findOverviewCounts(7L);
        verify(mapper).findRecentOrders(7L, 10);
        verify(mapper).findRecentTrackingExceptions(7L, 10);
    }

    @Test
    void nullAggregatePropertiesAreReturnedAsZero() {
        WarehouseOverviewMapper mapper = mock(WarehouseOverviewMapper.class);
        when(mapper.findOverviewCounts(7L)).thenReturn(new WarehouseOverviewCounts());
        when(mapper.findRecentOrders(7L, 10)).thenReturn(List.of());
        when(mapper.findRecentTrackingExceptions(7L, 10)).thenReturn(List.of());

        WarehouseOverview result = new WarehouseOverviewApplicationService(mapper).overview(7L);

        assertThat(result.pendingInbound()).isZero();
        assertThat(result.pendingMeasurement()).isZero();
        assertThat(result.pendingLabel()).isZero();
        assertThat(result.pendingHandover()).isZero();
        assertThat(result.pendingOutbound()).isZero();
        assertThat(result.inTransit()).isZero();
        assertThat(result.trackingExceptions()).isZero();
        assertThat(result.todayInbound()).isZero();
        assertThat(result.todayOutbound()).isZero();
    }

    private WarehouseOverviewCounts counts(long pendingInbound, long pendingMeasurement, long pendingLabel,
                                           long pendingHandover, long pendingOutbound, long inTransit,
                                           long trackingExceptions, long todayInbound, long todayOutbound) {
        WarehouseOverviewCounts counts = new WarehouseOverviewCounts();
        counts.setPendingInbound(pendingInbound);
        counts.setPendingMeasurement(pendingMeasurement);
        counts.setPendingLabel(pendingLabel);
        counts.setPendingHandover(pendingHandover);
        counts.setPendingOutbound(pendingOutbound);
        counts.setInTransit(inTransit);
        counts.setTrackingExceptions(trackingExceptions);
        counts.setTodayInbound(todayInbound);
        counts.setTodayOutbound(todayOutbound);
        return counts;
    }
}
