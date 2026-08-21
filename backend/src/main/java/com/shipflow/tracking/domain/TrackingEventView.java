package com.shipflow.tracking.domain;

import java.time.LocalDateTime;

/** Read-only projection for the paginated order tracking endpoint. */
public record TrackingEventView(
        Long id,
        String trackingNo,
        String eventId,
        String eventCode,
        String eventDescription,
        LocalDateTime eventTime,
        LocalDateTime receivedTime,
        String processStatus) {
}
