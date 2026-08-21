package com.shipflow.tracking.api.model;

import java.time.OffsetDateTime;

/** Public, sanitised tracking projection. Provider payloads and credentials are deliberately excluded. */
public record TrackingEventListItemResponse(
        Long id,
        Long orderId,
        String orderNo,
        String trackingNo,
        OffsetDateTime occurredAt,
        String location,
        String eventCode,
        String eventDescription,
        String source,
        String processStatus,
        boolean exception,
        String exceptionCode,
        Long exceptionId,
        String requestId) {
}
