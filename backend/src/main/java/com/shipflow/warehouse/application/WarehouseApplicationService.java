package com.shipflow.warehouse.application;

import com.shipflow.order.domain.model.ShipmentOrder;
import com.shipflow.warehouse.api.model.InboundRequest;
import com.shipflow.warehouse.api.model.MeasurementRequest;
import com.shipflow.warehouse.api.model.OutboundRequest;
import com.shipflow.warehouse.api.model.WarehouseResult;
import com.shipflow.warehouse.domain.WarehouseChargeableWeight;
import com.shipflow.warehouse.mapper.WarehouseMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.HexFormat;

@Service
public class WarehouseApplicationService {
    private static final BigDecimal MAX_MEASUREMENT_VALUE = new BigDecimal("1000000.000");
    private static final String INBOUND_OPERATION = "warehouseInbound";
    private static final String MEASUREMENT_OPERATION = "warehouseMeasurement";
    private static final String OUTBOUND_OPERATION = "warehouseOutbound";
    private static final DateTimeFormatter BATCH_TIME = DateTimeFormatter.ofPattern("yyyyMMddHHmmss")
            .withZone(ZoneOffset.UTC);

    private final WarehouseMapper mapper;
    private final Clock clock;

    public WarehouseApplicationService(WarehouseMapper mapper, Clock clock) {
        this.mapper = mapper;
        this.clock = clock;
    }

    @Transactional
    public WarehouseResult inbound(Long tenantId, Long userId, Long orderId, InboundRequest request, String requestId) {
        return inbound(tenantId, userId, orderId, request, null, requestId);
    }

    @Transactional
    public WarehouseResult inbound(Long tenantId, Long userId, Long orderId, InboundRequest request,
                                  String idempotencyKey, String requestId) {
        WarehouseResult replay = replay(tenantId, orderId, INBOUND_OPERATION, "inbound", idempotencyKey,
                hash(orderId + "\n" + request));
        if (replay != null) return replay;
        require(tenantId, orderId);
        transition(tenantId, orderId, "PENDING_INBOUND", "INBOUND", request.version(), "WAREHOUSE-1001");
        audit(tenantId, userId, orderId, "INBOUND", requestId);
        WarehouseResult result = result(require(tenantId, orderId));
        complete(tenantId, orderId, INBOUND_OPERATION, idempotencyKey);
        return result;
    }

    @Transactional
    public WarehouseResult measure(Long tenantId, Long userId, Long orderId, MeasurementRequest request, String requestId) {
        return measure(tenantId, userId, orderId, request, null, requestId);
    }

    @Transactional
    public WarehouseResult measure(Long tenantId, Long userId, Long orderId, MeasurementRequest request,
                                   String idempotencyKey, String requestId) {
        validateMeasurement(request);
        WarehouseResult replay = replay(tenantId, orderId, MEASUREMENT_OPERATION, "measurements", idempotencyKey,
                hash(orderId + "\n" + request));
        if (replay != null) return replay;
        ShipmentOrder order = require(tenantId, orderId);
        if (!"INBOUND".equals(order.currentStatus())) {
            throw new WarehouseException("WAREHOUSE-1002", 409);
        }
        Long packageId = mapper.packageId(tenantId, orderId);
        if (packageId == null) throw new WarehouseException("COMMON-1006", 404);

        BigDecimal divisor = new BigDecimal("5000");
        BigDecimal increment = new BigDecimal("0.5");
        BigDecimal volumeWeight = request.actualLength().multiply(request.actualWidth())
                .multiply(request.actualHeight()).divide(divisor, 3, RoundingMode.HALF_UP);
        BigDecimal chargeableWeight;
        try {
            chargeableWeight = WarehouseChargeableWeight.calculate(request.actualWeight(), request.actualLength(),
                    request.actualWidth(), request.actualHeight(), divisor, increment);
        } catch (RuntimeException exception) {
            throw new WarehouseException("WAREHOUSE-1002", 422);
        }

        LocalDateTime now = LocalDateTime.now(clock);
        mapper.insertMeasurement(tenantId, packageId, request.actualWeight(), request.actualLength(),
                request.actualWidth(), request.actualHeight(), volumeWeight, chargeableWeight, userId, now);
        BigDecimal currentFee = order.estimatedFee().multiply(chargeableWeight)
                .divide(order.chargeableWeight(), 2, RoundingMode.HALF_UP);
        String nextStatus = currentFee.compareTo(order.estimatedFee()) > 0
                ? "PENDING_PRICE_CONFIRMATION" : "READY_FOR_OUTBOUND";
        if (currentFee.compareTo(order.estimatedFee()) != 0) {
            Long measurementId = mapper.latestMeasurementId(tenantId, packageId);
            mapper.insertAdjustment(tenantId, orderId, measurementId,
                    currentFee.compareTo(order.estimatedFee()) > 0 ? "INCREASE" : "DECREASE",
                    order.estimatedFee(), currentFee, currentFee.subtract(order.estimatedFee()), order.currency());
        }
        if (mapper.updateFee(tenantId, orderId, chargeableWeight, currentFee, nextStatus, request.version()) != 1) {
            throw new WarehouseException("COMMON-1005", 409);
        }
        audit(tenantId, userId, orderId, "MEASURE", requestId);
        WarehouseResult result = result(require(tenantId, orderId));
        complete(tenantId, orderId, MEASUREMENT_OPERATION, idempotencyKey);
        return result;
    }

    @Transactional
    public WarehouseResult outbound(Long tenantId, Long userId, Long orderId, OutboundRequest request, String requestId) {
        return outbound(tenantId, userId, orderId, request, null, requestId);
    }

    @Transactional
    public WarehouseResult outbound(Long tenantId, Long userId, Long orderId, OutboundRequest request,
                                    String idempotencyKey, String requestId) {
        WarehouseResult replay = replay(tenantId, orderId, OUTBOUND_OPERATION, "outbound", idempotencyKey,
                hash(orderId + "\n" + request));
        if (replay != null) return replay;
        ShipmentOrder order = require(tenantId, orderId);
        if (!"READY_FOR_OUTBOUND".equals(order.currentStatus())) {
            throw new WarehouseException("WAREHOUSE-1004", 409);
        }
        Long packageId = mapper.packageId(tenantId, orderId);
        if (packageId == null) throw new WarehouseException("COMMON-1006", 404);
        String batchNo = "WH-" + BATCH_TIME.format(java.time.Instant.now(clock)) + "-" + orderId;
        String manifestReference = "MANIFEST-" + batchNo;
        try {
            if (mapper.outbound(tenantId, orderId, packageId, request.trackingNo(), userId,
                    LocalDateTime.now(clock), request.remark(), batchNo, manifestReference) != 1) {
                throw new WarehouseException("WAREHOUSE-1003", 422);
            }
        } catch (DuplicateKeyException exception) {
            throw new WarehouseException("WAREHOUSE-1005", 409);
        }
        transition(tenantId, orderId, "READY_FOR_OUTBOUND", "OUTBOUND", request.version(), "WAREHOUSE-1004");
        audit(tenantId, userId, orderId, "OUTBOUND", requestId);
        WarehouseResult result = result(require(tenantId, orderId));
        complete(tenantId, orderId, OUTBOUND_OPERATION, idempotencyKey);
        return result;
    }

    private WarehouseResult replay(Long tenantId, Long orderId, String operation, String path,
                                   String idempotencyKey,
                                   String requestHash) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) return null;
        if (idempotencyKey.length() > 128) throw new WarehouseException("COMMON-1001", 400);
        WarehouseMapper.IdempotencyRecord record = mapper.findIdempotency(tenantId, operation, idempotencyKey);
        if (record == null) {
            try {
                mapper.insertIdempotency(tenantId, operation, idempotencyKey, requestHash,
                        "/api/v1/orders/{orderId}/" + path,
                        LocalDateTime.now(clock).plusMinutes(30));
            } catch (DuplicateKeyException exception) {
                record = mapper.findIdempotency(tenantId, operation, idempotencyKey);
                if (record == null) throw exception;
            }
        }
        if (record == null) return null;
        if (!requestHash.equals(record.requestHash())) throw new WarehouseException("COMMON-1009", 409);
        if (record.resourceId() == null) throw new WarehouseException("COMMON-1010", 409);
        return result(require(tenantId, record.resourceId()));
    }

    private void complete(Long tenantId, Long orderId, String operation, String idempotencyKey) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            mapper.completeIdempotency(tenantId, operation, idempotencyKey, orderId);
        }
    }

    private ShipmentOrder require(Long tenantId, Long orderId) {
        ShipmentOrder order = mapper.findOrder(tenantId, orderId);
        if (order == null) throw new WarehouseException("COMMON-1006", 404);
        return order;
    }

    private void transition(Long tenantId, Long orderId, String from, String to, Long version, String code) {
        if (mapper.transition(tenantId, orderId, from, to, version) != 1) {
            throw new WarehouseException(code, 409);
        }
    }

    private void audit(Long tenantId, Long userId, Long orderId, String action, String requestId) {
        mapper.audit(tenantId, userId, orderId, action, requestId, LocalDateTime.now(clock));
    }

    private WarehouseResult result(ShipmentOrder order) {
        return new WarehouseResult(order.id(), order.currentStatus(), order.chargeableWeight(),
                order.estimatedFee(), order.version());
    }

    private void validateMeasurement(MeasurementRequest request) {
        if (request == null || request.actualWeight() == null || request.actualLength() == null
                || request.actualWidth() == null || request.actualHeight() == null
                || request.version() == null || request.version() < 0) {
            throw new WarehouseException("WAREHOUSE-1002", 422);
        }
        for (BigDecimal value : new BigDecimal[]{request.actualWeight(), request.actualLength(),
                request.actualWidth(), request.actualHeight()}) {
            if (value.signum() <= 0 || value.compareTo(MAX_MEASUREMENT_VALUE) > 0 || value.scale() > 3) {
                throw new WarehouseException("WAREHOUSE-1002", 422);
            }
        }
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
