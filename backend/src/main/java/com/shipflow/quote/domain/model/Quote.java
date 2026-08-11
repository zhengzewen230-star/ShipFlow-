package com.shipflow.quote.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/** Immutable tenant-scoped freight quote snapshot. All measurements are kg/cm and times are UTC. */
public record Quote(Long id, Long tenantId, String quoteNo, Long storeId, Long channelId, Long priceRuleId,
                    int ruleVersionNo, BigDecimal declaredWeight, BigDecimal declaredLength,
                    BigDecimal declaredWidth, BigDecimal declaredHeight, BigDecimal volumeWeight,
                    BigDecimal chargeableWeight, BigDecimal amount, String currency, String feeDetail,
                    LocalDateTime validFrom, LocalDateTime validTo, String status, long version,
                    LocalDateTime createdAt, LocalDateTime updatedAt) {
}
