package com.shipflow.quote.api.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Map;

/** Tenant-safe quote projection. Database UTC timestamps are exposed with an explicit UTC offset. */
public record QuoteResponse(
        Long id,
        String quoteNo,
        Long storeId,
        Long channelId,
        String destinationCountry,
        int ruleVersionNo,
        BigDecimal declaredWeight,
        BigDecimal declaredLength,
        BigDecimal declaredWidth,
        BigDecimal declaredHeight,
        BigDecimal declaredVolumeWeight,
        BigDecimal declaredChargeableWeight,
        BigDecimal amount,
        String currency,
        Map<String, Object> feeDetail,
        OffsetDateTime validFrom,
        OffsetDateTime validTo,
        String status,
        long version) {
}
