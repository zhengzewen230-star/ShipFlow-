package com.shipflow.operations.domain;

import java.time.LocalDateTime;

/** A read-only fact row that is counted by, and therefore reconciles to, one workbench metric. */
public record MetricDrilldownItem(
        String itemId, String resourceType, Long resourceId, Long orderId, Long storeId,
        String displayNo, String status, String source, LocalDateTime occurredAt) {
}
