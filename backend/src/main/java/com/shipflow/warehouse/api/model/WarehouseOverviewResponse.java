package com.shipflow.warehouse.api.model;

import com.shipflow.warehouse.domain.WarehouseOverview;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public record WarehouseOverviewResponse(
        long pendingInbound,
        long pendingMeasurement,
        long pendingLabel,
        long pendingHandover,
        long pendingOutbound,
        long inTransit,
        long trackingExceptions,
        long todayInbound,
        long todayOutbound,
        List<RecentOrder> recentOrders,
        List<TrackingException> recentTrackingExceptions) {

    public record RecentOrder(Long id, String orderNo, String trackingNo, String status,
                              String destinationCountry, java.math.BigDecimal chargeableWeight,
                              String action, OffsetDateTime createdAt) {
        public RecentOrder(Long id, String orderNo, String status, OffsetDateTime createdAt) {
            this(id, orderNo, null, status, null, null, null, createdAt);
        }
    }

    public record TrackingException(Long id, Long orderId, String orderNo, String trackingNo,
                                    String exceptionNo, String status, String description, OffsetDateTime reportedAt) {
        public TrackingException(Long id, Long orderId, String exceptionNo,
                                 String status, String description, OffsetDateTime reportedAt) {
            this(id, orderId, null, null, exceptionNo, status, description, reportedAt);
        }
    }

    public static WarehouseOverviewResponse from(WarehouseOverview overview) {
        return new WarehouseOverviewResponse(
                overview.pendingInbound(), overview.pendingMeasurement(), overview.pendingLabel(), overview.pendingHandover(),
                overview.pendingOutbound(), overview.inTransit(), overview.trackingExceptions(), overview.todayInbound(), overview.todayOutbound(),
                overview.recentOrders().stream()
                        .map(item -> new RecentOrder(item.id(), item.orderNo(), item.trackingNo(), item.status(),
                                item.destinationCountry(), item.chargeableWeight(), item.action(), utc(item.createdAt())))
                        .toList(),
                overview.recentTrackingExceptions().stream()
                        .map(item -> new TrackingException(item.id(), item.orderId(), item.orderNo(), item.trackingNo(),
                                item.exceptionNo(), item.status(), item.description(), utc(item.reportedAt())))
                        .toList());
    }

    private static OffsetDateTime utc(java.time.LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
