package com.shipflow.warehouse.api.model;

import java.util.List;

public record WarehouseWorkPageResponse(
        int page,
        int pageSize,
        long totalPages,
        long total,
        List<WarehouseWorkItemResponse> items) {
}
