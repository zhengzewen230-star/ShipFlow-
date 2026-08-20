package com.shipflow.tracking.api.model;

import java.util.List;

public record TrackingEventListPageResponse(
        int page,
        int pageSize,
        long total,
        int totalPages,
        List<TrackingEventListItemResponse> items) {
}
