package com.shipflow.order;

import com.shipflow.order.api.model.CreateShipmentOrderRequest;
import com.shipflow.order.api.model.OrderActionRequest;
import com.shipflow.order.api.model.UpdateShipmentOrderRequest;
import com.shipflow.order.application.ShipmentOrderException;
import com.shipflow.order.application.ShipmentOrderLifecycleApplicationService;
import com.shipflow.order.application.ShipmentOrderQueryApplicationService;
import com.shipflow.order.domain.model.ShipmentOrder;
import com.shipflow.order.mapper.ShipmentOrderMapper;
import com.shipflow.store.mapper.StoreScopeMapper;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

class ShipmentOrderLifecycleApplicationServiceTest {
    private final ShipmentOrderMapper mapper = mock(ShipmentOrderMapper.class);
    private final StoreScopeMapper storeScopeMapper = mock(StoreScopeMapper.class);
    private final ShipmentOrderQueryApplicationService query = new ShipmentOrderQueryApplicationService(mapper, storeScopeMapper);
    private final ShipmentOrderLifecycleApplicationService service = new ShipmentOrderLifecycleApplicationService(
            mapper, query, Clock.fixed(Instant.EPOCH, ZoneOffset.UTC));

    @Test
    void submitsOnlyDraftWithVersionAndReturnsAdvancedVersion() {
        when(mapper.findById(7L, 1L)).thenReturn(order("DRAFT", 3L), order("PENDING_INBOUND", 4L));
        when(mapper.transitionStatus(7L, 1L, "DRAFT", "PENDING_INBOUND", 3L)).thenReturn(1);

        var response = service.submit(7L, 2L, 1L, new OrderActionRequest(3L, null), "r");

        assertThat(response.status()).isEqualTo("PENDING_INBOUND");
        assertThat(response.version()).isEqualTo(4L);
        verify(mapper).insertAuditAction(7L, 2L, 1L, "SUBMIT", "r", "SUCCESS", null,
                LocalDateTime.of(1970, 1, 1, 0, 0));
    }

    @Test
    void advancesDraftVersionBeforeUpdatingAggregate() {
        var request = draftRequest(3L);
        when(mapper.findById(7L, 1L)).thenReturn(order("DRAFT", 3L), order("DRAFT", 4L));
        when(mapper.advanceDraftVersion(7L, 1L, 3L)).thenReturn(1);
        when(mapper.updateAddresses(7L, 1L, request.senderAddress(), request.receiverAddress())).thenReturn(2);

        var response = service.updateDraft(7L, 2L, 1L, request, "r");

        assertThat(response.version()).isEqualTo(4L);
        verify(mapper).advanceDraftVersion(7L, 1L, 3L);
        verify(mapper).insertDraftItem(7L, 1L, 1, request.items().get(0));
    }

    @Test
    void rejectsStaleDraftVersionBeforeChangingOrderDetails() {
        var request = draftRequest(2L);
        when(mapper.findById(7L, 1L)).thenReturn(order("DRAFT", 3L));
        when(mapper.advanceDraftVersion(7L, 1L, 2L)).thenReturn(0);

        assertThatThrownBy(() -> service.updateDraft(7L, 2L, 1L, request, "r"))
                .isInstanceOf(ShipmentOrderException.class)
                .satisfies(error -> assertThat(((ShipmentOrderException) error).code()).isEqualTo("COMMON-1005"));

        verify(mapper).findById(7L, 1L);
        verify(mapper).advanceDraftVersion(7L, 1L, 2L);
        verifyNoMoreInteractions(mapper);
    }

    @Test
    void rejectsBillingChangeInDraft() {
        when(mapper.findById(7L, 1L)).thenReturn(order("DRAFT", 0L));
        var address = address("CN");
        var request = new UpdateShipmentOrderRequest(0L, address, address, List.of(), null,
                null, null, BigDecimal.ONE, null, null, null, null, null, null, null, null, null);

        assertThatThrownBy(() -> service.updateDraft(7L, 2L, 1L, request, "r"))
                .isInstanceOf(ShipmentOrderException.class)
                .satisfies(error -> assertThat(((ShipmentOrderException) error).code()).isEqualTo("ORDER-1007"));
    }

    private UpdateShipmentOrderRequest draftRequest(Long version) {
        return new UpdateShipmentOrderRequest(version, address("CN"), address("US"),
                List.of(new CreateShipmentOrderRequest.Item("sku", "product", BigDecimal.ONE,
                        BigDecimal.TEN, "USD", "CN")), "draft",
                null, null, null, null, null, null, null, null, null, null, null, null);
    }

    private CreateShipmentOrderRequest.Address address(String country) {
        return new CreateShipmentOrderRequest.Address("name", "phone", null, null, country,
                null, "city", null, "line", null, "zip");
    }

    private ShipmentOrder order(String status, Long version) {
        return new ShipmentOrder(1L, 7L, "SO", "k", 3L, 9L, 4L, status, "CN", "US",
                BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE, BigDecimal.ONE,
                BigDecimal.ONE, BigDecimal.TEN, "USD", version, LocalDateTime.of(2026, 1, 1, 0, 0));
    }
}
