package com.shipflow.warehouse.domain;

import java.time.LocalDateTime;
import java.util.List;

/** Tenant-scoped warehouse workload projection. */
public record WarehouseOverview(
        long pendingInbound,
        long pendingMeasurement,
        long pendingLabel,
        long pendingHandover,
        long pendingOutbound,
        long inTransit,
        long trackingExceptions,
        long todayInbound,
        long todayOutbound,
        List<RecentWarehouseOrder> recentOrders,
        List<RecentTrackingException> recentTrackingExceptions) {

    public WarehouseOverview(long pendingInbound, long pendingMeasurement, long pendingLabel, long pendingHandover,
                             long inTransit, long trackingExceptions, long todayInbound, long todayOutbound,
                             List<RecentWarehouseOrder> recentOrders,
                             List<RecentTrackingException> recentTrackingExceptions) {
        this(pendingInbound, pendingMeasurement, pendingLabel, pendingHandover, pendingHandover, inTransit,
                trackingExceptions, todayInbound, todayOutbound, recentOrders, recentTrackingExceptions);
    }

    /** Compatibility constructor for callers compiled against the original seven-count projection. */
    public WarehouseOverview(long pendingInbound, long pendingMeasurement, long pendingOutbound, long inTransit,
                             long trackingExceptions, long todayInbound, long todayOutbound,
                             List<RecentWarehouseOrder> recentOrders,
                             List<RecentTrackingException> recentTrackingExceptions) {
        this(pendingInbound, pendingMeasurement, 0L, pendingOutbound, pendingOutbound, inTransit,
                trackingExceptions, todayInbound, todayOutbound, recentOrders, recentTrackingExceptions);
    }

    public record RecentWarehouseOrder(Long id, String orderNo, String trackingNo, String status,
                                       String destinationCountry, java.math.BigDecimal chargeableWeight,
                                       String action, LocalDateTime createdAt) {
        public RecentWarehouseOrder(Long id, String orderNo, String status, LocalDateTime createdAt) {
            this(id, orderNo, null, status, null, null, null, createdAt);
        }
    }

    public record RecentTrackingException(Long id, Long orderId, String orderNo, String trackingNo,
                                          String exceptionNo, String status, String description,
                                          LocalDateTime reportedAt) {
        public RecentTrackingException(Long id, Long orderId, String exceptionNo,
                                       String status, String description, LocalDateTime reportedAt) {
            this(id, orderId, null, null, exceptionNo, status, description, reportedAt);
        }
    }
}
