package com.shipflow.operations.application;

import com.shipflow.operations.domain.OperationsSummary;
import com.shipflow.operations.domain.OperationsTodos;
import com.shipflow.operations.domain.OperationsWorkbenchQuery;
import com.shipflow.operations.domain.WorkbenchCounts;
import com.shipflow.operations.api.model.OperationsWorkbenchResponse;
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
        List<OperationsWorkbenchResponse.Metric> metrics = metrics(counts, range, refreshedAt, normalized.storeId());
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

    private List<OperationsWorkbenchResponse.Metric> metrics(WorkbenchCounts c,
                                                               OperationsWorkbenchResponse.TimeRange range,
                                                               OffsetDateTime refreshedAt, Long storeId) {
        return List.of(
                metric("PENDING_ORDERS", "待处理订单", c.pendingOrders(), range, refreshedAt, "/app/orders", "SHIPMENT_ORDER", storeId, null),
                metric("PENDING_INBOUND", "待入库", c.pendingInbound(), range, refreshedAt, "/app/warehouse", "WAREHOUSE", storeId, "PENDING_INBOUND"),
                metric("PENDING_MEASUREMENT", "待复称", c.pendingMeasurement(), range, refreshedAt, "/app/warehouse", "WAREHOUSE", storeId, "INBOUND"),
                metric("PENDING_LABEL", "待贴标/打单", c.pendingLabel(), range, refreshedAt, "/app/warehouse", "WAREHOUSE", storeId, "PENDING_LABEL"),
                metric("PENDING_OUTBOUND", "待出库", c.pendingOutbound(), range, refreshedAt, "/app/warehouse", "WAREHOUSE", storeId, "PENDING_OUTBOUND"),
                metric("IN_TRANSIT", "在途订单", c.inTransit(), range, refreshedAt, "/app/tracking", "TRACKING", storeId, "IN_TRANSIT"),
                metric("TRACKING_EXCEPTION", "轨迹异常", c.trackingException(), range, refreshedAt, "/app/tracking", "TRACKING", storeId, "TRACKING_EXCEPTION"),
                new OperationsWorkbenchResponse.Metric("PENDING_FINANCE", "待财务处理",
                        c.pendingFeeConfirmation() + c.billImportErrors() + c.reconciliationDifference() + c.pendingFinanceReview(),
                        range, refreshedAt, target("/app/billing", "FINANCE", storeId, null), List.of(
                        breakdown("PENDING_FEE_CONFIRMATION", "待确认费用", c.pendingFeeConfirmation(), "/app/orders", storeId),
                        breakdown("BILL_IMPORT_ERRORS", "账单导入错误", c.billImportErrors(), "/app/billing", storeId),
                        breakdown("RECONCILIATION_DIFFERENCE", "费用对账差异", c.reconciliationDifference(), "/app/billing", storeId),
                        breakdown("PENDING_FINANCE_REVIEW", "待财务复核", c.pendingFinanceReview(), "/app/exceptions", storeId)))
        );
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
        return new OperationsWorkbenchResponse.Metric(key, label, count, range, refreshedAt, target(route, type, storeId, status), List.of());
    }

    private OperationsWorkbenchResponse.Breakdown breakdown(String key, String label, long count, String route, Long storeId) {
        return new OperationsWorkbenchResponse.Breakdown(key, label, count, target(route, "FINANCE", storeId, key));
    }

    private OperationsWorkbenchResponse.Todo todo(String key, String label, long count,
                                                   OperationsWorkbenchResponse.TimeRange range, String route, Long storeId) {
        return new OperationsWorkbenchResponse.Todo(key, label, count, range, target(route, key, storeId, key));
    }

    private OperationsWorkbenchResponse.Target target(String route, String type, Long storeId, String status) {
        Map<String, Object> query = new LinkedHashMap<>();
        query.put("resourceType", type);
        if (storeId != null) query.put("storeId", storeId);
        if (status != null) query.put("status", status);
        return new OperationsWorkbenchResponse.Target(route, query, type);
    }

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
