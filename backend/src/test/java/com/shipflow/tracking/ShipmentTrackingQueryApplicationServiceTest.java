package com.shipflow.tracking;

import com.shipflow.order.application.ShipmentOrderException;
import com.shipflow.tracking.application.ShipmentTrackingQueryApplicationService;
import com.shipflow.tracking.domain.ShipmentTrackingEvent;
import com.shipflow.tracking.domain.ShipmentTrackingOrder;
import com.shipflow.tracking.mapper.ShipmentTrackingMapper;
import com.shipflow.store.mapper.StoreScopeMapper;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ShipmentTrackingQueryApplicationServiceTest {
    @Test
    void resolvesOrderOrWaybillWithinTenantAndMapsUtcTimeline() {
        ShipmentTrackingMapper mapper = mock(ShipmentTrackingMapper.class);
        StoreScopeMapper scope = mock(StoreScopeMapper.class);
        when(mapper.findOrderByReference(7L, "UAT-SF-1"))
                .thenReturn(new ShipmentTrackingOrder(9L, "UAT-SF-1", "SF-1", 12L));
        when(scope.canAccessStore(7L, 2L, 12L)).thenReturn(true);
        when(mapper.findTimeline(7L, 9L)).thenReturn(List.of(new ShipmentTrackingEvent(
                1L, 9L, "UAT-SF-1", "SF-1", "DELIVERED", "快件已妥投签收", "signed", "收件地址",
                "SF_EXPRESS", LocalDateTime.of(2026, 8, 16, 8, 0))));

        var result = new ShipmentTrackingQueryApplicationService(mapper, scope).list(7L, 2L, "UAT-SF-1");

        assertThat(result).singleElement().satisfies(event -> {
            assertThat(event.orderNo()).isEqualTo("UAT-SF-1");
            assertThat(event.occurredAt().getOffset().getId()).isEqualTo("Z");
        });
        verify(mapper).findTimeline(7L, 9L);
    }

    @Test
    void hidesOrdersOutsideTenantAndRejectsBlankReference() {
        ShipmentTrackingMapper mapper = mock(ShipmentTrackingMapper.class);
        StoreScopeMapper scope = mock(StoreScopeMapper.class);
        when(mapper.findOrderByReference(7L, "OTHER")).thenReturn(null);
        var service = new ShipmentTrackingQueryApplicationService(mapper, scope);

        assertThatThrownBy(() -> service.list(7L, 2L, "OTHER"))
                .isInstanceOf(ShipmentOrderException.class)
                .extracting(error -> ((ShipmentOrderException) error).code()).isEqualTo("COMMON-1006");
        assertThatThrownBy(() -> service.list(7L, 2L, " "))
                .isInstanceOf(ShipmentOrderException.class)
                .extracting(error -> ((ShipmentOrderException) error).code()).isEqualTo("COMMON-1001");
    }
}
