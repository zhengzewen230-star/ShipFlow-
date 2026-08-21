package com.shipflow.tracking.application;

import com.shipflow.order.application.ShipmentOrderException;
import com.shipflow.tracking.api.model.TrackingEventResponse;
import com.shipflow.tracking.api.model.TrackingEventViewResponse;
import com.shipflow.tracking.api.model.TrackingPageResponse;
import com.shipflow.tracking.api.model.TrackingStatusResponse;
import com.shipflow.tracking.domain.TrackingEvent;
import com.shipflow.tracking.domain.TrackingEventView;
import com.shipflow.tracking.mapper.TrackingQueryMapper;
import com.shipflow.store.mapper.StoreScopeMapper;
import org.springframework.stereotype.Service;

import java.time.ZoneOffset;
import java.time.OffsetDateTime;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.List;

/** Read-only tenant-scoped tracking projections; no callback or event mutation occurs here. */
@Service
public class TrackingQueryApplicationService {
    private final TrackingQueryMapper mapper;
    private final StoreScopeMapper storeScope;

    public TrackingQueryApplicationService(TrackingQueryMapper mapper, StoreScopeMapper storeScope) {
        this.mapper = mapper;
        this.storeScope = storeScope;
    }

    public List<TrackingEventResponse> list(Long tenantId, Long userId, Long orderId) {
        require(tenantId, userId, orderId);
        return mapper.findEvents(tenantId, orderId).stream().map(this::response).toList();
    }

    public TrackingPageResponse page(Long tenantId, Long userId, Long orderId, int page, int pageSize) {
        require(tenantId, userId, orderId);
        if (page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ShipmentOrderException("COMMON-1001", 400);
        }
        long total = mapper.countEvents(tenantId, orderId);
        return new TrackingPageResponse(page, pageSize, total,
                (int) ((total + pageSize - 1) / pageSize),
                mapper.findEventPage(tenantId, orderId, (page - 1) * pageSize, pageSize)
                        .stream().map(this::viewResponse).toList());
    }

    public TrackingStatusResponse status(Long tenantId, Long userId, Long orderId) {
        require(tenantId, userId, orderId);
        TrackingEvent latest = mapper.findLatest(tenantId, orderId);
        return new TrackingStatusResponse(orderId, mapper.currentStatus(tenantId, orderId),
                latest == null ? null : response(latest));
    }

    public com.shipflow.tracking.api.model.TrackingEventListPageResponse pageForCaller(
            Long tenantId, Long userId, String orderNo, String trackingNo, String statusCode,
            OffsetDateTime from, OffsetDateTime to, int page, int pageSize, String sortDirection) {
        if (tenantId == null || tenantId < 1 || userId == null || userId < 1
                || page < 1 || pageSize < 1 || pageSize > 100) {
            throw new ShipmentOrderException("COMMON-1001", 400);
        }
        if (from != null && to != null && from.isAfter(to)) {
            throw new ShipmentOrderException("COMMON-1001", 400);
        }
        String normalizedStatus = normalize(statusCode, 64);
        String normalizedOrderNo = normalize(orderNo, 64);
        String normalizedTrackingNo = normalize(trackingNo, 128);
        String direction = "ASC".equalsIgnoreCase(sortDirection) ? "ASC" : "DESC";
        LocalDateTime fromUtc = from == null ? null : from.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        LocalDateTime toUtc = to == null ? null : to.withOffsetSameInstant(ZoneOffset.UTC).toLocalDateTime();
        long total = mapper.countUnifiedEventsForCaller(tenantId, userId, normalizedOrderNo, normalizedTrackingNo,
                normalizedStatus, fromUtc, toUtc);
        var items = mapper.findUnifiedEventsForCaller(tenantId, userId, normalizedOrderNo, normalizedTrackingNo,
                        normalizedStatus, fromUtc, toUtc, direction, (page - 1) * pageSize, pageSize)
                .stream().map(event -> new com.shipflow.tracking.api.model.TrackingEventListItemResponse(
                        event.id(), event.orderId(), event.orderNo(), event.trackingNo(),
                        event.occurredAt().atOffset(ZoneOffset.UTC), event.location(), event.eventCode(),
                        event.eventDescription(), event.source(), event.processStatus(),
                        event.exceptionCode() != null, event.exceptionCode(), event.exceptionId(), event.requestId())).toList();
        return new com.shipflow.tracking.api.model.TrackingEventListPageResponse(page, pageSize, total,
                (int) ((total + pageSize - 1) / pageSize), items);
    }

    private String normalize(String value, int maxLength) {
        if (value == null || value.isBlank()) return null;
        String trimmed = value.trim();
        if (trimmed.length() > maxLength || !trimmed.matches("^[A-Za-z0-9_\\-]+$")) {
            throw new ShipmentOrderException("COMMON-1001", 400);
        }
        return trimmed.toUpperCase(Locale.ROOT);
    }

    private void require(Long tenant, Long userId, Long order) {
        if (tenant == null || tenant < 1 || userId == null || userId < 1) {
            throw new ShipmentOrderException("COMMON-1004", 403);
        }
        Long storeId = order == null ? null : mapper.findOrderStoreId(tenant, order);
        if (storeId == null || !mapper.orderExists(tenant, order) || !storeScope.canAccessStore(tenant, userId, storeId)) {
            throw new ShipmentOrderException("COMMON-1006", 404);
        }
    }

    private TrackingEventResponse response(TrackingEvent event) {
        return new TrackingEventResponse(event.id(), event.trackingNo(), event.eventCode(), event.description(),
                event.eventTime().atOffset(ZoneOffset.UTC), event.processStatus());
    }

    private TrackingEventViewResponse viewResponse(TrackingEventView event) {
        return new TrackingEventViewResponse(event.id(), event.trackingNo(), event.eventId(), event.eventCode(),
                event.eventDescription(), event.eventTime().atOffset(ZoneOffset.UTC),
                event.receivedTime().atOffset(ZoneOffset.UTC), event.processStatus());
    }
}
