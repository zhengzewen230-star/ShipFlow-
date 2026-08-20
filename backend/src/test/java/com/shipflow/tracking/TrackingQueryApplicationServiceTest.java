package com.shipflow.tracking;

import com.shipflow.order.application.ShipmentOrderException;
import com.shipflow.tracking.application.TrackingQueryApplicationService;
import com.shipflow.tracking.domain.TrackingEvent;
import com.shipflow.tracking.domain.TrackingEventView;
import com.shipflow.tracking.mapper.TrackingQueryMapper;
import com.shipflow.store.mapper.StoreScopeMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class TrackingQueryApplicationServiceTest {
    @Test
    void listsTenantEventsAndReturnsLatestStatus() {
        TrackingQueryMapper mapper = mock(TrackingQueryMapper.class);
        StoreScopeMapper scope = mock(StoreScopeMapper.class);
        when(mapper.orderExists(7L, 9L)).thenReturn(true);
        when(mapper.findOrderStoreId(7L, 9L)).thenReturn(12L);
        when(scope.canAccessStore(7L, 2L, 12L)).thenReturn(true);
        var first = new TrackingEvent(1L, "T", "PICKED", null,
                LocalDateTime.of(2026, 1, 1, 1, 0), "PROCESSED");
        var latest = new TrackingEvent(2L, "T", "DELIVERED", null,
                LocalDateTime.of(2026, 1, 2, 1, 0), "PROCESSED");
        when(mapper.findEvents(7L, 9L)).thenReturn(List.of(first, latest));
        when(mapper.currentStatus(7L, 9L)).thenReturn("DELIVERED");
        when(mapper.findLatest(7L, 9L)).thenReturn(latest);

        var service = new TrackingQueryApplicationService(mapper, scope);

        assertThat(service.list(7L, 2L, 9L)).extracting(x -> x.id()).containsExactly(1L, 2L);
        assertThat(service.status(7L, 2L, 9L).latestEvent().eventCode()).isEqualTo("DELIVERED");
    }

    @Test
    void pagesOnlyEventsBelongingToTheTenantOrder() {
        TrackingQueryMapper mapper = mock(TrackingQueryMapper.class);
        StoreScopeMapper scope = mock(StoreScopeMapper.class);
        when(mapper.orderExists(7L, 9L)).thenReturn(true);
        when(mapper.findOrderStoreId(7L, 9L)).thenReturn(12L);
        when(scope.canAccessStore(7L, 2L, 12L)).thenReturn(true);
        when(mapper.countEvents(7L, 9L)).thenReturn(1L);
        when(mapper.findEventPage(7L, 9L, 0, 20)).thenReturn(List.of(new TrackingEventView(
                1L, "T-1", "EV-1", "PICKED", "Handed to carrier",
                LocalDateTime.of(2026, 1, 1, 1, 0), LocalDateTime.of(2026, 1, 1, 1, 1), "PROCESSED")));

        var page = new TrackingQueryApplicationService(mapper, scope).page(7L, 2L, 9L, 1, 20);

        assertThat(page.total()).isEqualTo(1L);
        assertThat(page.items()).singleElement().extracting(item -> item.eventId()).isEqualTo("EV-1");
        verify(mapper).findEventPage(7L, 9L, 0, 20);
    }

    @Test
    void rejectsOrdersOutsideTheTenantAndInvalidPageSize() {
        TrackingQueryMapper mapper = mock(TrackingQueryMapper.class);
        StoreScopeMapper scope = mock(StoreScopeMapper.class);
        when(mapper.orderExists(7L, 9L)).thenReturn(false);
        var service = new TrackingQueryApplicationService(mapper, scope);

        assertThatThrownBy(() -> service.page(7L, 2L, 9L, 1, 20))
                .isInstanceOf(ShipmentOrderException.class)
                .extracting(exception -> ((ShipmentOrderException) exception).code()).isEqualTo("COMMON-1006");
        when(mapper.orderExists(7L, 9L)).thenReturn(true);
        when(mapper.findOrderStoreId(7L, 9L)).thenReturn(12L);
        when(scope.canAccessStore(7L, 2L, 12L)).thenReturn(true);
        assertThatThrownBy(() -> service.page(7L, 2L, 9L, 1, 101))
                .isInstanceOf(ShipmentOrderException.class)
                .extracting(exception -> ((ShipmentOrderException) exception).code()).isEqualTo("COMMON-1001");
    }
}
