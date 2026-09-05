package com.shipflow.operations.api.model;

import com.shipflow.operations.domain.MetricDrilldownItem;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public record OperationsMetricDrilldownPageResponse(
        String metricKey, String metricLabel, String definition, String dataSource, String timeField, String unit,
        OperationsWorkbenchResponse.TimeRange timeRange, int page, int pageSize, long total, int totalPages,
        List<Item> items) {
    public static OperationsMetricDrilldownPageResponse of(String key, String label, String definition,
                                                            String dataSource, String timeField, String unit,
                                                            OperationsWorkbenchResponse.TimeRange range,
                                                            int page, int pageSize, long total,
                                                            List<MetricDrilldownItem> items) {
        return new OperationsMetricDrilldownPageResponse(key, label, definition, dataSource, timeField, unit, range,
                page, pageSize, total, total == 0 ? 0 : (int) Math.ceil((double) total / pageSize),
                items.stream().map(Item::from).toList());
    }

    public record Item(String itemId, String resourceType, Long resourceId, Long orderId, Long storeId,
                       String displayNo, String status, String source, OffsetDateTime occurredAt) {
        static Item from(MetricDrilldownItem item) {
            return new Item(item.itemId(), item.resourceType(), item.resourceId(), item.orderId(), item.storeId(),
                    item.displayNo(), item.status(), item.source(),
                    item.occurredAt() == null ? null : item.occurredAt().atOffset(ZoneOffset.UTC));
        }
    }
}
