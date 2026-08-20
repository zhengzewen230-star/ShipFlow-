package com.shipflow.tracking.application;

import com.shipflow.order.application.ShipmentOrderException;
import com.shipflow.tracking.api.model.ShipmentTrackingEventResponse;
import com.shipflow.tracking.domain.ShipmentTrackingEvent;
import com.shipflow.tracking.domain.ShipmentTrackingOrder;
import com.shipflow.tracking.mapper.ShipmentTrackingMapper;
import com.shipflow.store.mapper.StoreScopeMapper;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.util.List;

@Service
public class ShipmentTrackingQueryApplicationService {
    private final ShipmentTrackingMapper mapper;
    private final StoreScopeMapper storeScope;

    public ShipmentTrackingQueryApplicationService(ShipmentTrackingMapper mapper, StoreScopeMapper storeScope) {
        this.mapper = mapper;
        this.storeScope = storeScope;
    }

    public List<ShipmentTrackingEventResponse> list(Long tenantId, Long userId, String reference) {
        if (tenantId == null || tenantId < 1) {
            throw new ShipmentOrderException("COMMON-1004", 403);
        }
        if (reference == null || reference.isBlank() || reference.length() > 128) {
            throw new ShipmentOrderException("COMMON-1001", 400);
        }
        ShipmentTrackingOrder order = mapper.findOrderByReference(tenantId, reference.trim());
        if (order == null || !storeScope.canAccessStore(tenantId, userId, order.storeId())) {
            throw new ShipmentOrderException("COMMON-1006", 404);
        }
        return mapper.findTimeline(tenantId, order.id()).stream().map(this::response).toList();
    }

    private ShipmentTrackingEventResponse response(ShipmentTrackingEvent event) {
        return new ShipmentTrackingEventResponse(event.id(), event.orderId(), event.orderNo(), event.waybillNo(),
                event.statusCode(), event.title(), event.description(), event.location(), event.source(),
                event.occurredAt().atOffset(ZoneOffset.UTC));
    }
}
