package com.shipflow.operations.api.model;

import com.shipflow.operations.domain.WorkbenchRecentOrder;
import com.shipflow.operations.domain.WorkbenchRisk;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.List;

public record OperationsWorkbenchResponse(
        String businessTimeZone,
        Scope scope,
        TimeRange timeRange,
        OffsetDateTime refreshedAt,
        List<Metric> metrics,
        List<Todo> todos,
        List<RecentOrder> recentOrders,
        List<Risk> risks) {

    public record Scope(Long tenantId, List<Long> storeIds, String scopeType) { }

    public record TimeRange(String preset, OffsetDateTime from, OffsetDateTime to) { }

    public record Target(String route, java.util.Map<String, Object> query, String resourceType) { }

    public record Breakdown(String key, String label, long count, Target target) { }

    public record Metric(String key, String label, long count, TimeRange window,
                         OffsetDateTime refreshedAt, Target target, List<Breakdown> breakdown) { }

    public record Todo(String key, String label, long count, TimeRange window, Target target) { }

    public record RecentOrder(Long orderId, String orderNo, Long storeId, String storeName,
                              String destination, String orderStatus, String labelStatus,
                              BigDecimal chargeableWeight, BigDecimal estimatedFee, String currency,
                              String sfTrackingNo, OffsetDateTime updatedAt, String nextAction,
                              Target target) {
        public static RecentOrder from(WorkbenchRecentOrder value, TimeRange window,
                                       OffsetDateTime refreshedAt) {
            String action = switch (value.orderStatus()) {
                case "PENDING_INBOUND" -> "确认入库";
                case "INBOUND" -> "提交复称";
                case "READY_FOR_OUTBOUND" -> "LABEL_READY".equals(value.labelStatus()) ? "确认出库" : "获取面单";
                case "OUTBOUND", "IN_TRANSIT" -> "查看轨迹";
                default -> "查看详情";
            };
            String route = "IN_TRANSIT".equals(value.orderStatus()) || "OUTBOUND".equals(value.orderStatus())
                    ? "/app/tracking" : "/app/orders";
            return new RecentOrder(value.orderId(), value.orderNo(), value.storeId(), value.storeName(),
                    value.destination(), value.orderStatus(), value.labelStatus(), value.chargeableWeight(),
                    value.estimatedFee(), value.currency(), value.sfTrackingNo(), utc(value.updatedAt()), action,
                    new Target(route, java.util.Map.of("orderId", value.orderId(), "storeId", value.storeId()), "SHIPMENT_ORDER"));
        }
    }

    public record Risk(String id, String type, String level, String title, String resourceType,
                       Long resourceId, Long storeId, OffsetDateTime occurredAt,
                       String description, Target target) {
        public static Risk from(WorkbenchRisk value) {
            String route = "TRACKING_EXCEPTION".equals(value.type()) ? "/app/tracking"
                    : "BILL_IMPORT_ERRORS".equals(value.type()) || "RECONCILIATION_DIFFERENCE".equals(value.type())
                    ? "/app/billing" : "QUOTE".equals(value.resourceType()) ? "/app/quotes" : "/app/orders";
            return new Risk(value.id(), value.type(), value.level(), value.title(), value.resourceType(),
                    value.resourceId(), value.storeId(), utc(value.occurredAt()), value.description(),
                    new Target(route, java.util.Map.of("resourceId", value.resourceId()), value.resourceType()));
        }
    }

    private static OffsetDateTime utc(java.time.LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }
}
