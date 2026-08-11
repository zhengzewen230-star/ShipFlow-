package com.shipflow.logistics.domain.model;

import java.math.BigDecimal;

/** Immutable platform price-rule tier. Ranges are [minWeight, maxWeight). */
public record PriceRuleTier(
        int tierNo,
        BigDecimal minWeight,
        BigDecimal maxWeight,
        BillingMode billingMode,
        BigDecimal firstWeight,
        BigDecimal firstFee,
        BigDecimal additionalWeight,
        BigDecimal additionalFee,
        BigDecimal tierFee) {
    public enum BillingMode { FIXED, FIRST_CONTINUE }
}
