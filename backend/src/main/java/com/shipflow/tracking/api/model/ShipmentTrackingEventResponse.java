package com.shipflow.tracking.api.model;

import java.time.OffsetDateTime;

public record ShipmentTrackingEventResponse(
        Long id,
        Long orderId,
        String orderNo,
        String waybillNo,
        String statusCode,
        String title,
        String description,
        String location,
        String source,
        OffsetDateTime occurredAt) {
}
