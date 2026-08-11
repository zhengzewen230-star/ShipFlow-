package com.shipflow.tracking.api.model;

import java.util.List;

public record TrackingCallbackResult(int accepted, int duplicated, int rejected,
                                     List<TrackingEventResult> events) {
    public TrackingCallbackResult {
        events = List.copyOf(events);
    }
}
