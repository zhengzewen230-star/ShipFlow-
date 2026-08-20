package com.shipflow.warehouse;

import com.shipflow.warehouse.application.WarehouseWorkApplicationService;
import com.shipflow.warehouse.domain.WarehouseWorkItem;
import com.shipflow.warehouse.mapper.WarehouseWorkMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WarehouseWorkApplicationServiceTest {
    @Test
    void listsTenantScopedWorkAndMapsFeeAlert() {
        WarehouseWorkMapper mapper = mock(WarehouseWorkMapper.class);
        when(mapper.count(7L, "INBOUND", "TEST")).thenReturn(1L);
        when(mapper.findPage(7L, "INBOUND", "TEST", 0, 20)).thenReturn(List.of(item(new BigDecimal("2.50"))));

        var page = new WarehouseWorkApplicationService(mapper).list(7L, "INBOUND", "TEST", 1, 20);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.items()).singleElement().satisfies(value -> {
            assertThat(value.feeDifference()).isEqualByComparingTo("2.50");
            assertThat(value.feeAlert()).isTrue();
            assertThat(value.warehouseStatus()).isEqualTo("PENDING_MEASUREMENT");
        });
        verify(mapper).count(7L, "INBOUND", "TEST");
    }

    @Test
    void emptyTenantWorkReturnsEmptyPage() {
        WarehouseWorkMapper mapper = mock(WarehouseWorkMapper.class);
        when(mapper.count(7L, null, null)).thenReturn(0L);
        when(mapper.findPage(7L, null, null, 0, 20)).thenReturn(List.of());
        var page = new WarehouseWorkApplicationService(mapper).list(7L, null, null, 1, 20);
        assertThat(page.items()).isEmpty();
        assertThat(page.total()).isZero();
    }

    private WarehouseWorkItem item(BigDecimal difference) {
        return new WarehouseWorkItem(9L, "TEST-ORDER", null, 7L, "测试商户", "US",
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.TEN, BigDecimal.ONE,
                new BigDecimal("2.5"), new BigDecimal("10"), new BigDecimal("12.5"), "USD", difference,
                "PENDING_MEASUREMENT", "INBOUND", 1L, null, null, LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}
