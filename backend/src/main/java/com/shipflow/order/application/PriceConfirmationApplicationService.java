package com.shipflow.order.application;

import com.shipflow.order.api.model.PriceConfirmationRequest;
import com.shipflow.order.api.model.PriceConfirmationView;
import com.shipflow.order.mapper.PriceConfirmationIdempotencyMapper;
import com.shipflow.order.mapper.PriceConfirmationMapper;
import com.shipflow.store.mapper.StoreScopeMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;

@Service
public class PriceConfirmationApplicationService {
    private static final String REQUEST_OPERATION = "submitPriceConfirmationRequest";
    private static final String CONFIRM_OPERATION = "confirmPrice";

    private final PriceConfirmationMapper mapper;
    private final PriceConfirmationIdempotencyMapper idempotency;
    private final StoreScopeMapper storeScope;
    private final Clock clock;

    public PriceConfirmationApplicationService(PriceConfirmationMapper mapper,
                                               PriceConfirmationIdempotencyMapper idempotency,
                                               StoreScopeMapper storeScope,
                                               Clock clock) {
        this.mapper = mapper;
        this.idempotency = idempotency;
        this.storeScope = storeScope;
        this.clock = clock;
    }

    public PriceConfirmationView get(Long tenantId, Long userId, Long orderId) {
        PriceConfirmationMapper.OrderRecord order = requireOrder(tenantId, orderId);
        requireStoreAccess(tenantId, userId, order.storeId());
        PriceConfirmationMapper.AdjustmentRecord adjustment = mapper.findLatestAdjustment(tenantId, orderId);
        if (adjustment == null) throw new ShipmentOrderException("COMMON-1006", 404);
        return view(order, adjustment);
    }

    @Transactional
    public PriceConfirmationView request(Long tenantId, Long userId, Long orderId,
                                         PriceConfirmationRequest request, String idempotencyKey,
                                         String requestId) {
        requireRequest(request);
        PriceConfirmationMapper.OrderRecord order = requireOrder(tenantId, orderId);
        requireRequestAccess(tenantId, userId, order.storeId());
        String requestHash = hash(orderId + "\n" + request);
        PriceConfirmationIdempotencyMapper.Record prior = claim(tenantId, REQUEST_OPERATION,
                idempotencyKey, "/api/v1/orders/{orderId}/price-confirmation-requests", requestHash);
        if (prior != null) return get(tenantId, userId, orderId);

        requirePendingOrder(order);
        PriceConfirmationMapper.AdjustmentRecord adjustment = requireAdjustment(tenantId, orderId, request.feeAdjustmentId());
        validateIncrease(order, adjustment, request.expectedFee());
        if (!"PENDING_CONFIRMATION".equals(adjustment.confirmationStatus())) {
            throw new ShipmentOrderException("ORDER-1010", 409);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (mapper.markRequested(tenantId, orderId, adjustment.id(), userId, now) != 1) {
            throw new ShipmentOrderException("COMMON-1005", 409);
        }
        mapper.audit(tenantId, userId, orderId, "PRICE_CONFIRMATION_REQUEST", requestId,
                "SUCCESS", null, now);
        idempotency.complete(tenantId, REQUEST_OPERATION, idempotencyKey, orderId);
        return get(tenantId, userId, orderId);
    }

    @Transactional
    public PriceConfirmationView confirm(Long tenantId, Long userId, Long orderId,
                                         PriceConfirmationRequest request, String idempotencyKey,
                                         String requestId) {
        requireRequest(request);
        PriceConfirmationMapper.OrderRecord order = requireOrder(tenantId, orderId);
        requireConfirmAccess(tenantId, userId, order.storeId());
        String requestHash = hash(orderId + "\n" + request);
        PriceConfirmationIdempotencyMapper.Record prior = claim(tenantId, CONFIRM_OPERATION,
                idempotencyKey, "/api/v1/orders/{orderId}/price-confirmation", requestHash);
        if (prior != null) return get(tenantId, userId, orderId);

        requirePendingOrder(order);
        PriceConfirmationMapper.AdjustmentRecord adjustment = requireAdjustment(tenantId, orderId, request.feeAdjustmentId());
        validateIncrease(order, adjustment, request.expectedFee());
        if (!"PENDING_CONFIRMATION".equals(adjustment.confirmationStatus())
                && !"REQUESTED".equals(adjustment.confirmationStatus())) {
            throw new ShipmentOrderException("ORDER-1010", 409);
        }
        LocalDateTime now = LocalDateTime.now(clock);
        if (mapper.confirmOrder(tenantId, orderId, request.expectedFee(), request.version()) != 1) {
            throw new ShipmentOrderException("COMMON-1005", 409);
        }
        if (mapper.confirmAdjustment(tenantId, orderId, adjustment.id(), request.expectedFee(), userId, now) != 1) {
            throw new ShipmentOrderException("COMMON-1005", 409);
        }
        mapper.audit(tenantId, userId, orderId, "PRICE_CONFIRMATION", requestId,
                "SUCCESS", null, now);
        idempotency.complete(tenantId, CONFIRM_OPERATION, idempotencyKey, orderId);
        return get(tenantId, userId, orderId);
    }

    private PriceConfirmationIdempotencyMapper.Record claim(Long tenantId, String operation,
                                                              String key, String path, String requestHash) {
        if (tenantId == null || userIdInvalid(tenantId) || key == null || key.isBlank() || key.length() > 128) {
            throw new ShipmentOrderException("COMMON-1001", 400);
        }
        PriceConfirmationIdempotencyMapper.Record record = idempotency.find(tenantId, operation, key);
        if (record == null) {
            try {
                idempotency.insert(tenantId, operation, key, path, requestHash,
                        LocalDateTime.now(clock).plusMinutes(30));
            } catch (DuplicateKeyException exception) {
                record = idempotency.find(tenantId, operation, key);
                if (record == null) throw exception;
            }
        }
        if (record == null) return null;
        if (!requestHash.equals(record.requestHash())) throw new ShipmentOrderException("COMMON-1009", 409);
        if (record.resourceId() == null || "PROCESSING".equals(record.processingStatus())) {
            throw new ShipmentOrderException("COMMON-1010", 409);
        }
        return record;
    }

    private boolean userIdInvalid(Long tenantId) {
        return tenantId < 1;
    }

    private void requireRequest(PriceConfirmationRequest request) {
        if (request == null || request.feeAdjustmentId() == null || request.expectedFee() == null
                || request.expectedFee().signum() < 0 || request.version() == null || request.version() < 0) {
            throw new ShipmentOrderException("COMMON-1001", 400);
        }
    }

    private PriceConfirmationMapper.OrderRecord requireOrder(Long tenantId, Long orderId) {
        if (tenantId == null || tenantId < 1 || orderId == null || orderId < 1) {
            throw new ShipmentOrderException("COMMON-1004", 403);
        }
        PriceConfirmationMapper.OrderRecord order = mapper.findOrder(tenantId, orderId);
        if (order == null) throw new ShipmentOrderException("COMMON-1006", 404);
        return order;
    }

    private PriceConfirmationMapper.AdjustmentRecord requireAdjustment(Long tenantId, Long orderId, Long adjustmentId) {
        PriceConfirmationMapper.AdjustmentRecord adjustment = mapper.findAdjustment(tenantId, orderId, adjustmentId);
        if (adjustment == null) throw new ShipmentOrderException("COMMON-1006", 404);
        return adjustment;
    }

    private void requireStoreAccess(Long tenantId, Long userId, Long storeId) {
        if (userId == null || !storeScope.canAccessStore(tenantId, userId, storeId)) {
            throw new ShipmentOrderException("COMMON-1006", 404);
        }
    }

    private void requireRequestAccess(Long tenantId, Long userId, Long storeId) {
        if (userId == null || !storeScope.canRequestPriceConfirmation(tenantId, userId, storeId)) {
            throw new ShipmentOrderException("COMMON-1004", 403);
        }
    }

    private void requireConfirmAccess(Long tenantId, Long userId, Long storeId) {
        if (userId == null || !storeScope.canConfirmPrice(tenantId, userId, storeId)) {
            throw new ShipmentOrderException("COMMON-1004", 403);
        }
    }

    private void requirePendingOrder(PriceConfirmationMapper.OrderRecord order) {
        if (!"PENDING_PRICE_CONFIRMATION".equals(order.currentStatus())) {
            throw new ShipmentOrderException("ORDER-1010", 409);
        }
    }

    private void validateIncrease(PriceConfirmationMapper.OrderRecord order,
                                  PriceConfirmationMapper.AdjustmentRecord adjustment,
                                  BigDecimal expectedFee) {
        if (!order.id().equals(adjustment.orderId()) || !order.storeId().equals(adjustment.storeId())
                || !"INCREASE".equals(adjustment.adjustmentType())) {
            throw new ShipmentOrderException("COMMON-1006", 404);
        }
        if (adjustment.afterAmount() == null || adjustment.afterAmount().compareTo(expectedFee) != 0) {
            throw new ShipmentOrderException("ORDER-1011", 422);
        }
    }

    private PriceConfirmationView view(PriceConfirmationMapper.OrderRecord order,
                                       PriceConfirmationMapper.AdjustmentRecord adjustment) {
        return new PriceConfirmationView(order.id(), order.orderNo(), order.quoteId(), order.storeId(),
                order.currentStatus(), order.estimatedFee(), order.currentFee(), order.confirmedFee(), order.currency(),
                order.chargeableWeight(), order.version(), adjustment.id(), adjustment.adjustmentType(),
                adjustment.beforeAmount(), adjustment.afterAmount(), adjustment.differenceAmount(),
                adjustment.confirmationStatus(), utc(adjustment.requestedAt()), utc(adjustment.confirmedAt()));
    }

    private java.time.OffsetDateTime utc(LocalDateTime value) {
        return value == null ? null : value.atOffset(ZoneOffset.UTC);
    }

    private String hash(String value) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (Exception exception) {
            throw new IllegalStateException(exception);
        }
    }
}
