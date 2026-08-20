package com.shipflow.warehouse.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WarehouseWorkItem(
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
        String warehouseStatus,
        String logisticsStatus,
        Long version,
        LocalDateTime outboundAt,
        Long outboundBy,
        LocalDateTime createdAt) {
}
