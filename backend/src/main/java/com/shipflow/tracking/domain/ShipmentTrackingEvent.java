package com.shipflow.tracking.domain;

import java.time.LocalDateTime;

public record ShipmentTrackingEvent(
        Long id,
        Long orderId,
        String orderNo,
        String waybillNo,
        String statusCode,
        String title,
        String description,
        String location,
        String source,
        LocalDateTime occurredAt) {
}
