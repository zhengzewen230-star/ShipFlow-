package com.shipflow.operations.domain;

import java.time.OffsetDateTime;

public record OperationsWorkbenchQuery(String timeRange, OffsetDateTime from, OffsetDateTime to,
                                       Long storeId, Integer page, Integer pageSize, String sortBy,
                                       String sortDirection, Integer recentLimit, Integer riskLimit) {
    public static OperationsWorkbenchQuery defaults() {
        return new OperationsWorkbenchQuery("TODAY", null, null, null, 1, 20,
                "updatedAt", "DESC", 10, 10);
    }
}
