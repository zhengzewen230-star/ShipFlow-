package com.shipflow.tracking.domain;

public record TrackingCallbackOrder(Long tenantId, Long orderId, String currentStatus) {
}
