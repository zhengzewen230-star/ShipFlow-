package com.shipflow.quote.domain;

import com.shipflow.logistics.domain.model.PriceRuleTier;
import com.shipflow.logistics.domain.model.PublishedPriceRule;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.List;

/** Deterministic calculator for a published platform price-rule version. */
public final class QuoteCalculator {

    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private QuoteCalculator() {
    }

    public static Calculation calculate(PublishedPriceRule rule, BigDecimal declaredWeight,
                                        BigDecimal length, BigDecimal width, BigDecimal height) {
        requirePositive(declaredWeight, "declaredWeight");
        requirePositive(length, "declaredLength");
        requirePositive(width, "declaredWidth");
        requirePositive(height, "declaredHeight");

        BigDecimal volumeWeight = length.multiply(width).multiply(height)
                .divide(rule.volumeDivisor(), 3, RoundingMode.HALF_UP);
        BigDecimal chargeableWeight = round(max(declaredWeight, volumeWeight), rule.roundingMode(), rule.roundingIncrement());
        PriceRuleTier tier = selectTier(rule.tiers(), chargeableWeight);
        BigDecimal amount = fee(tier, chargeableWeight).setScale(2, RoundingMode.HALF_UP);
        return new Calculation(volumeWeight, chargeableWeight, amount, tier.tierNo());
    }

    private static PriceRuleTier selectTier(List<PriceRuleTier> tiers, BigDecimal weight) {
        return tiers.stream()
                .filter(tier -> weight.compareTo(tier.minWeight()) >= 0
                        && (tier.maxWeight() == null || weight.compareTo(tier.maxWeight()) < 0))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("no price tier covers chargeable weight"));
    }

    private static BigDecimal fee(PriceRuleTier tier, BigDecimal chargeableWeight) {
        if (tier.billingMode() == PriceRuleTier.BillingMode.FIXED) {
            return tier.tierFee();
        }
        BigDecimal additionalUnits = chargeableWeight.subtract(tier.firstWeight()).max(ZERO)
                .divide(tier.additionalWeight(), 0, RoundingMode.CEILING);
        return tier.firstFee().add(additionalUnits.multiply(tier.additionalFee()));
    }

    private static BigDecimal round(BigDecimal value, PublishedPriceRule.RoundingMode mode, BigDecimal increment) {
        if (mode == PublishedPriceRule.RoundingMode.NONE) {
            return value;
        }
        RoundingMode rounding = mode == PublishedPriceRule.RoundingMode.CEILING ? RoundingMode.CEILING : RoundingMode.HALF_UP;
        return value.divide(increment, 0, rounding).multiply(increment);
    }

    private static BigDecimal max(BigDecimal first, BigDecimal second) {
        return first.compareTo(second) >= 0 ? first : second;
    }

    private static void requirePositive(BigDecimal value, String name) {
        if (value == null || value.compareTo(ZERO) <= 0) {
            throw new IllegalArgumentException(name + " must be positive");
        }
    }

    public record Calculation(BigDecimal volumeWeight, BigDecimal chargeableWeight, BigDecimal amount, int tierNo) {
    }
}
