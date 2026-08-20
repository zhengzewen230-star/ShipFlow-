package com.shipflow.warehouse.api.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

/** Warehouse work projection backed only by currently deployed tables. */
public record WarehouseWorkItemResponse(
        Long id,
        String businessOrderNo,
        String sfTrackingNo,
        Long tenantId,
        String tenantName,
        String destinationCountry,
        BigDecimal declaredWeight,
        BigDecimal declaredLength,
        BigDecimal declaredWidth,
        BigDecimal declaredHeight,
        BigDecimal declaredVolumeWeight,
        BigDecimal actualWeight,
        BigDecimal actualLength,
        BigDecimal actualWidth,
        BigDecimal actualHeight,
        BigDecimal actualVolumeWeight,
        BigDecimal chargeableWeight,
        BigDecimal estimatedFee,
        BigDecimal currentFee,
        String currency,
        BigDecimal feeDifference,
        boolean feeAlert,
        String warehouseStatus,
        String logisticsStatus,
        Long version,
        OffsetDateTime outboundAt,
        Long outboundBy,
        OffsetDateTime createdAt) {
}
