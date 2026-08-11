package com.shipflow.tracking.domain;

public record ExistingTrackingEvent(Long id, String rawPayload, String processStatus) {
}
