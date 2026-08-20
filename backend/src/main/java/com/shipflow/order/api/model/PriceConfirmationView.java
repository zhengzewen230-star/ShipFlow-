package com.shipflow.order.api.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record PriceConfirmationView(
        Long id,
        String orderNo,
        Long quoteId,
        Long storeId,
        String currentStatus,
        BigDecimal estimatedFee,
        BigDecimal currentFee,
        BigDecimal confirmedFee,
        String currency,
        BigDecimal chargeableWeight,
        Long version,
        Long feeAdjustmentId,
        String adjustmentType,
        BigDecimal beforeAmount,
        BigDecimal afterAmount,
        BigDecimal differenceAmount,
        String confirmationStatus,
        OffsetDateTime requestedAt,
        OffsetDateTime confirmedAt) {
}
