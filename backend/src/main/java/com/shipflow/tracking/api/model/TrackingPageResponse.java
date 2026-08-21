package com.shipflow.tracking.api.model;

import java.util.List;

public record TrackingPageResponse(int page, int pageSize, long total, int totalPages,
                                   List<TrackingEventViewResponse> items) {
}
