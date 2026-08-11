package com.shipflow.tracking.api.model;

public record TrackingEventResult(String eventId, String status, String errorCode, String message) {
    public static TrackingEventResult accepted(String eventId) {
        return new TrackingEventResult(eventId, "ACCEPTED", null, null);
    }

    public static TrackingEventResult duplicate(String eventId) {
        return new TrackingEventResult(eventId, "DUPLICATE", null, null);
    }
}
