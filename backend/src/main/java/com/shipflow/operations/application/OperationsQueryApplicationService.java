package com.shipflow.operations.application;

import com.shipflow.operations.domain.OperationsSummary;
import com.shipflow.operations.domain.OperationsTodos;
import com.shipflow.operations.domain.OperationsWorkbenchQuery;
import com.shipflow.operations.domain.WorkbenchCounts;
import com.shipflow.operations.api.model.OperationsWorkbenchResponse;
import com.shipflow.operations.api.model.OperationsMetricDrilldownPageResponse;
import com.shipflow.operations.domain.MetricDrilldownItem;
import com.shipflow.operations.mapper.OperationsMapper;
import com.shipflow.order.application.ShipmentOrderException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.*;
import java.util.*;

@Service
public class OperationsQueryApplicationService {
    private final OperationsMapper mapper;
    private final Clock clock;

    @Autowired
    public OperationsQueryApplicationService(OperationsMapper mapper) {
        this(mapper, Clock.systemUTC());
    }

    public OperationsQueryApplicationService(OperationsMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }
    public OperationsSummary summary(Long tenantId) { return mapper.findSummary(tenantId); }
    public OperationsTodos todos(Long tenantId) { return mapper.findTodos(tenantId); }

    @Transactional(readOnly = true)
    public OperationsWorkbenchResponse workbench(Long tenantId, Long userId, OperationsWorkbenchQuery query) {
        requireCaller(tenantId, userId);
        OperationsWorkbenchQuery normalized = normalize(query == null ? OperationsWorkbenchQuery.defaults() : query);
        Window window = window(normalized);
        List<Long> visibleStores = mapper.findVisibleStoreIds(tenantId, userId, normalized.storeId());
        if (normalized.storeId() != null && (visibleStores == null || !visibleStores.contains(normalized.storeId()))) {
            throw new ShipmentOrderException("COMMON-1004", 403);
        }
        List<Long> stores = visibleStores == null ? List.of() : List.copyOf(visibleStores);
        WorkbenchCounts counts = Optional.ofNullable(mapper.findWorkbenchCounts(tenantId, userId,
                window.from(), window.to(), normalized.storeId())).orElseGet(() -> new WorkbenchCounts(
                0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0));
        OffsetDateTime refreshedAt = OffsetDateTime.now(clock).withOffsetSameInstant(ZoneOffset.UTC);
        OperationsWorkbenchResponse.TimeRange range = new OperationsWorkbenchResponse.TimeRange(
                normalized.timeRange(), window.from().atOffset(ZoneOffset.UTC), window.to().atOffset(ZoneOffset.UTC));
        Map<String, Long> metricTotals = metricTotals(tenantId, userId, window, normalized.storeId());
        List<OperationsWorkbenchResponse.Metric> metrics = metrics(counts, metricTotals, range, refreshedAt, normalized.storeId());
        List<OperationsWorkbenchResponse.Todo> todos = todos(counts, range, normalized.storeId());
        var recent = Optional.ofNullable(mapper.findRecentOrders(tenantId, userId, window.from(), window.to(), normalized.storeId(),
                normalized.sortBy(), normalized.sortDirection(), (normalized.page() - 1) * normalized.pageSize(), normalized.recentLimit())
                ).orElseGet(List::of).stream().map(item -> OperationsWorkbenchResponse.RecentOrder.from(item, range, refreshedAt)).toList();
        LocalDateTime asOf = refreshedAt.toLocalDateTime();
        var risks = Optional.ofNullable(mapper.findRisks(tenantId, userId, window.from(), window.to(), normalized.storeId(), asOf, 24,
                normalized.riskLimit())).orElseGet(List::of).stream().map(OperationsWorkbenchResponse.Risk::from).toList();
        return new OperationsWorkbenchResponse("Asia/Shanghai",
                new OperationsWorkbenchResponse.Scope(tenantId, stores,
                        normalized.storeId() == null ? "ALL_TENANT_STORES" : "AUTHORIZED_STORES"),
                range, refreshedAt, metrics, todos, recent, risks);
    }

    @Transactional(readOnly = true)
    public OperationsMetricDrilldownPageResponse metricDrilldown(Long tenantId, Long userId, String metricKey,
                                                                   OperationsWorkbenchQuery query) {
        requireCaller(tenantId, userId);
        OperationsWorkbenchQuery normalized = normalize(query == null ? OperationsWorkbenchQuery.defaults() : query);
        String key = requireMetricKey(metricKey);
        Window window = window(normalized);
        List<Long> visibleStores = mapper.findVisibleStoreIds(tenantId, userId, normalized.storeId());
        if (normalized.storeId() != null && (visibleStores == null || !visibleStores.contains(normalized.storeId()))) {
            throw new ShipmentOrderException("COMMON-1004", 403);
        }
        long total = mapper.countMetricDrilldown(tenantId, userId, window.from(), window.to(), normalized.storeId(), key);
        List<MetricDrilldownItem> items = Optional.ofNullable(mapper.findMetricDrilldown(tenantId, userId,
                window.from(), window.to(), normalized.storeId(), key,
                (normalized.page() - 1) * normalized.pageSize(), normalized.pageSize())).orElseGet(List::of);
        MetricContract contract = metricContract(key);
        return OperationsMetricDrilldownPageResponse.of(key, metricLabel(key), contract.definition(), contract.dataSource(),
                contract.timeField(), contract.unit(), new OperationsWorkbenchResponse.TimeRange(normalized.timeRange(),
                        window.from().atOffset(ZoneOffset.UTC), window.to().atOffset(ZoneOffset.UTC)),
                normalized.page(), normalized.pageSize(), total, items);
    }

    private Map<String, Long> metricTotals(Long tenantId, Long userId, Window window, Long storeId) {
        Map<String, Long> totals = new LinkedHashMap<>();
        for (String key : metricKeys()) totals.put(key,
                mapper.countMetricDrilldown(tenantId, userId, window.from(), window.to(), storeId, key));
        return totals;
    }

    private List<OperationsWorkbenchResponse.Metric> metrics(WorkbenchCounts c, Map<String, Long> totals,
                                                               OperationsWorkbenchResponse.TimeRange range,
                                                               OffsetDateTime refreshedAt, Long storeId) {
        List<OperationsWorkbenchResponse.Metric> aggregateMetrics = List.of(
                metric("PENDING_ORDERS", "待处理订单", c.pendingOrders(), range, refreshedAt, "/app/orders", "SHIPMENT_ORDER", storeId, null),
                metric("PENDING_INBOUND", "待入库", c.pendingInbound(), range, refreshedAt, "/app/warehouse", "WAREHOUSE", storeId, "PENDING_INBOUND"),
                metric("PENDING_MEASUREMENT", "待复称", c.pendingMeasurement(), range, refreshedAt, "/app/warehouse", "WAREHOUSE", storeId, "INBOUND"),
                metric("PENDING_LABEL", "待贴标/打单", c.pendingLabel(), range, refreshedAt, "/app/warehouse", "WAREHOUSE", storeId, "PENDING_LABEL"),
                metric("PENDING_OUTBOUND", "待出库", c.pendingOutbound(), range, refreshedAt, "/app/warehouse", "WAREHOUSE", storeId, "PENDING_OUTBOUND"),
                metric("IN_TRANSIT", "在途订单", c.inTransit(), range, refreshedAt, "/app/tracking", "TRACKING", storeId, "IN_TRANSIT"),
                metric("TRACKING_EXCEPTION", "轨迹异常", c.trackingException(), range, refreshedAt, "/app/tracking", "TRACKING", storeId, "TRACKING_EXCEPTION"),
                new OperationsWorkbenchResponse.Metric("PENDING_FINANCE", "待财务处理",
                        c.pendingFeeConfirmation() + c.billImportErrors() + c.reconciliationDifference() + c.pendingFinanceReview(),
                        range, refreshedAt, target("/app/billing", "FINANCE", storeId, null, range), List.of(
                        breakdown("PENDING_FEE_CONFIRMATION", "待确认费用", c.pendingFeeConfirmation(), "/app/orders", storeId, range),
                        breakdown("BILL_IMPORT_ERRORS", "账单导入错误", c.billImportErrors(), "/app/billing", storeId, range),
                        breakdown("RECONCILIATION_DIFFERENCE", "费用对账差异", c.reconciliationDifference(), "/app/billing", storeId, range),
                        breakdown("PENDING_FINANCE_REVIEW", "待财务复核", c.pendingFinanceReview(), "/app/exceptions", storeId, range)),
                        "费用确认、账单错误、非零对账差异和索赔财务复核的待处理任务总数；同一订单的不同任务分别计数。",
                        "fee_adjustment + bill_import_batch/bill_detail + reconciliation_record + exception_case",
                        "各事实表 created_at", "TASK_COUNT")
        );
        return aggregateMetrics.stream().map(metric -> new OperationsWorkbenchResponse.Metric(
                metric.key(), metric.label(), totals.getOrDefault(metric.key(), 0L), metric.window(), metric.refreshedAt(),
                metricTarget(metric.key(), storeId, range), metric.breakdown(), metric.definition(), metric.dataSource(),
                metric.timeField(), metric.unit())).toList();
    }

    private List<OperationsWorkbenchResponse.Todo> todos(WorkbenchCounts c, OperationsWorkbenchResponse.TimeRange range, Long storeId) {
        return List.of(
                todo("PENDING_FEE_CONFIRMATION", "待确认费用", c.pendingFeeConfirmation(), range, "/app/orders", storeId),
                todo("MISSING_ADDRESS", "待补充地址", c.missingAddress(), range, "/app/orders", storeId),
                todo("MISSING_CUSTOMS_DOCUMENT", "待补充清关资料", c.missingCustomsDocument(), range, "/app/warehouse", storeId),
                todo("PENDING_WAREHOUSE", "待仓库处理", c.pendingWarehouse(), range, "/app/warehouse", storeId),
                todo("PENDING_EXCEPTION_FOLLOW_UP", "待异常跟进", c.pendingExceptionFollowUp(), range, "/app/exceptions", storeId),
                todo("PENDING_RECONCILIATION", "待对账确认", c.pendingReconciliation(), range, "/app/billing", storeId));
    }

    private OperationsWorkbenchResponse.Metric metric(String key, String label, long count,
                                                       OperationsWorkbenchResponse.TimeRange range,
                                                       OffsetDateTime refreshedAt, String route, String type, Long storeId, String status) {
        MetricContract contract = metricContract(key);
        return new OperationsWorkbenchResponse.Metric(key, label, count, range, refreshedAt,
                target(route, type, storeId, status, range), List.of(), contract.definition(),
                contract.dataSource(), contract.timeField(), contract.unit());
    }

    private OperationsWorkbenchResponse.Breakdown breakdown(String key, String label, long count, String route,
                                                             Long storeId, OperationsWorkbenchResponse.TimeRange range) {
        return new OperationsWorkbenchResponse.Breakdown(key, label, count, target(route, "FINANCE", storeId, key, range));
    }

    private OperationsWorkbenchResponse.Target metricTarget(String key, Long storeId,
                                                            OperationsWorkbenchResponse.TimeRange range) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("metricKey", key);
        if (storeId != null) query.put("storeId", storeId);
        query.put("timeRange", range.preset());
        query.put("from", range.from().toString());
        query.put("to", range.to().toString());
        return new OperationsWorkbenchResponse.Target("/app/workbench/metrics/" + key, query, "WORKBENCH_METRIC");
    }

    private OperationsWorkbenchResponse.Todo todo(String key, String label, long count,
                                                   OperationsWorkbenchResponse.TimeRange range, String route, Long storeId) {
        return new OperationsWorkbenchResponse.Todo(key, label, count, range, target(route, key, storeId, key, range));
    }

    private OperationsWorkbenchResponse.Target target(String route, String type, Long storeId, String status,
                                                       OperationsWorkbenchResponse.TimeRange range) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("resourceType", type);
        if (storeId != null) query.put("storeId", storeId);
        if (status != null) query.put("status", status);
        if (range != null) {
            query.put("timeRange", range.preset());
            query.put("from", range.from().toString());
            query.put("to", range.to().toString());
        }
        return new OperationsWorkbenchResponse.Target(route, query, type);
    }

    private MetricContract metricContract(String key) {
        return switch (key) {
            case "PENDING_ORDERS" -> new MetricContract(
                    "统计窗口内发生更新且仍处于未完成履约状态的去重订单数。",
                    "shipment_order", "updated_at", "ORDER_COUNT");
            case "PENDING_INBOUND" -> new MetricContract(
                    "统计窗口内更新且当前状态为待入库的订单数。",
                    "shipment_order", "updated_at", "ORDER_COUNT");
            case "PENDING_MEASUREMENT" -> new MetricContract(
                    "统计窗口内更新、已入库且尚无仓库复称事实的订单数。",
                    "shipment_order + shipment_package + warehouse_measurement", "shipment_order.updated_at", "ORDER_COUNT");
            case "PENDING_LABEL" -> new MetricContract(
                    "统计窗口内更新、已可出库但尚无就绪面单的订单数。",
                    "shipment_order + provider_order", "shipment_order.updated_at", "ORDER_COUNT");
            case "PENDING_OUTBOUND" -> new MetricContract(
                    "统计窗口内更新、已可出库且面单已就绪的订单数。",
                    "shipment_order + provider_order", "shipment_order.updated_at", "ORDER_COUNT");
            case "IN_TRANSIT" -> new MetricContract(
                    "统计窗口内更新且当前处于运输中的订单数。",
                    "shipment_order", "updated_at", "ORDER_COUNT");
            case "TRACKING_EXCEPTION" -> new MetricContract(
                    "统计窗口内收到 RETRY 或 REJECTED 轨迹事件所关联的去重订单数。",
                    "tracking_event + shipment_order", "tracking_event.received_time", "ORDER_COUNT");
            default -> new MetricContract("后端事实表中的真实待处理任务数。", "business fact tables", "created_at", "TASK_COUNT");
        };
    }

    private List<String> metricKeys() {
        return List.of("PENDING_ORDERS", "PENDING_INBOUND", "PENDING_MEASUREMENT", "PENDING_LABEL",
                "PENDING_OUTBOUND", "IN_TRANSIT", "TRACKING_EXCEPTION", "PENDING_FINANCE");
    }

    private String requireMetricKey(String value) {
        String key = value == null ? "" : value.trim().toUpperCase(Locale.ROOT);
        if (!metricKeys().contains(key)) badRequest();
        return key;
    }

    private String metricLabel(String key) {
        return switch (key) {
            case "PENDING_ORDERS" -> "待处理订单";
            case "PENDING_INBOUND" -> "待入库";
            case "PENDING_MEASUREMENT" -> "待复秤";
            case "PENDING_LABEL" -> "待贴标/打单";
            case "PENDING_OUTBOUND" -> "待出库";
            case "IN_TRANSIT" -> "在途订单";
            case "TRACKING_EXCEPTION" -> "轨迹异常";
            case "PENDING_FINANCE" -> "待财务处理";
            default -> throw new IllegalArgumentException("unknown metric");
        };
    }

    private record MetricContract(String definition, String dataSource, String timeField, String unit) { }

    private OperationsWorkbenchQuery normalize(OperationsWorkbenchQuery q) {
        String preset = q.timeRange() == null || q.timeRange().isBlank() ? "TODAY" : q.timeRange().toUpperCase(Locale.ROOT);
        int page = q.page() == null ? 1 : q.page();
        int pageSize = q.pageSize() == null ? 20 : q.pageSize();
        int recentLimit = q.recentLimit() == null ? 10 : q.recentLimit();
        int riskLimit = q.riskLimit() == null ? 10 : q.riskLimit();
        String sortBy = q.sortBy() == null || q.sortBy().isBlank() ? "updatedAt" : q.sortBy();
        String sortDirection = q.sortDirection() == null || q.sortDirection().isBlank() ? "DESC" : q.sortDirection().toUpperCase(Locale.ROOT);
        if (!Set.of("TODAY", "LAST_7_DAYS", "LAST_30_DAYS", "CUSTOM").contains(preset)
                || page < 1 || pageSize < 1 || pageSize > 100 || recentLimit < 1 || recentLimit > 50
                || riskLimit < 1 || riskLimit > 50 || q.storeId() != null && q.storeId() < 1) badRequest();
        if (!Set.of("updatedAt", "createdAt").contains(sortBy) || !Set.of("ASC", "DESC").contains(sortDirection)) badRequest();
        return new OperationsWorkbenchQuery(preset, q.from(), q.to(), q.storeId(), page, pageSize, sortBy, sortDirection, recentLimit, riskLimit);
    }

    private Window window(OperationsWorkbenchQuery q) {
        ZoneId shanghai = ZoneId.of("Asia/Shanghai");
        if ("CUSTOM".equals(q.timeRange())) {
            if (q.from() == null || q.to() == null || !q.to().isAfter(q.from())) badRequest();
            return new Window(q.from().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime(), q.to().withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime());
        }
        ZonedDateTime end = ZonedDateTime.now(clock).withZoneSameInstant(shanghai).toLocalDate().plusDays(1).atStartOfDay(shanghai);
        ZonedDateTime start = "LAST_7_DAYS".equals(q.timeRange()) ? end.minusDays(7)
                : "LAST_30_DAYS".equals(q.timeRange()) ? end.minusDays(30) : end.minusDays(1);
        return new Window(start.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime(), end.withZoneSameInstant(ZoneOffset.UTC).toLocalDateTime());
    }

    private void requireCaller(Long tenantId, Long userId) {
        if (tenantId == null || tenantId < 1 || userId == null || userId < 1) throw new ShipmentOrderException("COMMON-1004", 403);
    }

    private void badRequest() { throw new ShipmentOrderException("COMMON-1001", 400); }

    private record Window(LocalDateTime from, LocalDateTime to) { }
}
