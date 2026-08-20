package com.shipflow.order;

import com.shipflow.order.application.ShipmentOrderException;
import com.shipflow.order.application.ShipmentOrderQueryApplicationService;
import com.shipflow.order.domain.model.ShipmentOrder;
import com.shipflow.order.mapper.ShipmentOrderMapper;
import com.shipflow.store.mapper.StoreScopeMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.*;

class ShipmentOrderQueryApplicationServiceTest {
    private final ShipmentOrderMapper mapper = mock(ShipmentOrderMapper.class);
    private final ShipmentOrderQueryApplicationService service = new ShipmentOrderQueryApplicationService(mapper, mock(StoreScopeMapper.class));

    @Test
    void appliesWhitelistedWorkbenchFilterInsideCallerScope() {
        when(mapper.countForCaller(7L, 2L, null, "MISSING_ADDRESS", 3L)).thenReturn(1L);
        when(mapper.findPageForCaller(7L, 2L, null, "MISSING_ADDRESS", 3L, 20, 20)).thenReturn(List.of(order()));

        var page = service.listForCaller(7L, 2L, null, "missing_address", 3L, 2, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).extracting(item -> item.id()).containsExactly(9L);
        verify(mapper).countForCaller(7L, 2L, null, "MISSING_ADDRESS", 3L);
        verify(mapper).findPageForCaller(7L, 2L, null, "MISSING_ADDRESS", 3L, 20, 20);
    }

    @Test
    void rejectsUnknownWorkbenchFilterBeforeQuerying() {
        assertThatThrownBy(() -> service.listForCaller(7L, 2L, null, "PENDING_OUTBOUND", null, 1, 20))
                .isInstanceOf(ShipmentOrderException.class)
                .satisfies(error -> {
                    var orderError = (ShipmentOrderException) error;
                    assertThat(orderError.code()).isEqualTo("COMMON-1001");
                    assertThat(orderError.status()).isEqualTo(400);
                });
        verifyNoInteractions(mapper);
    }

    private ShipmentOrder order() {
        return new ShipmentOrder(9L, 7L, "SO-9", "key", 3L, 8L, 4L, "DRAFT", "CN", "US",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, new BigDecimal("12.50"), "USD", 0L, LocalDateTime.of(2026, 8, 18, 0, 0));
    }
}
