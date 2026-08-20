package com.shipflow.tracking.domain;

import java.time.LocalDateTime;

public record TrackingEventListItem(
        Long id,
        Long orderId,
        String orderNo,
        String trackingNo,
        LocalDateTime occurredAt,
        String location,
        String eventCode,
        String eventDescription,
        String source,
        String processStatus,
        String exceptionCode,
        Long exceptionId,
        String requestId) {
}
