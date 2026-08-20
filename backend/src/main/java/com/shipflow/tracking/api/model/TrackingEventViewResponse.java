package com.shipflow.tracking.api.model;

import java.time.OffsetDateTime;

public record TrackingEventViewResponse(
        Long id,
        String trackingNo,
        String eventId,
        String eventCode,
        String eventDescription,
        OffsetDateTime eventTime,
        OffsetDateTime receivedTime,
        String processStatus) {
}
