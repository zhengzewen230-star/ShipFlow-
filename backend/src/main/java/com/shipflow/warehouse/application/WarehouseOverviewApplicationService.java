package com.shipflow.warehouse.application;

import com.shipflow.warehouse.domain.WarehouseOverview;
import com.shipflow.warehouse.domain.WarehouseOverviewCounts;
import com.shipflow.warehouse.mapper.WarehouseOverviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class WarehouseOverviewApplicationService {
    private final WarehouseOverviewMapper mapper;

    public WarehouseOverviewApplicationService(WarehouseOverviewMapper mapper) {
        this.mapper = mapper;
    }

    @Transactional(readOnly = true)
    public WarehouseOverview overview(Long tenantId) {
        if (tenantId == null || tenantId < 1) {
            throw new WarehouseException("COMMON-1004", 403);
        }
        WarehouseOverviewCounts counts = mapper.findOverviewCounts(tenantId);
        long pendingHandover = counts.getPendingHandover() == null
                ? value(counts.getPendingOutbound()) : value(counts.getPendingHandover());
        return new WarehouseOverview(value(counts.getPendingInbound()), value(counts.getPendingMeasurement()),
                value(counts.getPendingLabel()), pendingHandover, pendingHandover,
                value(counts.getInTransit()), value(counts.getTrackingExceptions()),
                value(counts.getTodayInbound()), value(counts.getTodayOutbound()),
                mapper.findRecentOrders(tenantId, 10), mapper.findRecentTrackingExceptions(tenantId, 10));
    }

    private long value(Long value) {
        return value == null ? 0L : value;
    }
}
