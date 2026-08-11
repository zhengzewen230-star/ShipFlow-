package com.shipflow.logistics.domain;

import com.shipflow.logistics.domain.model.PriceRuleTier;

import java.math.BigDecimal;
import java.util.Comparator;
import java.util.List;

/** Validates a publishable, gap-free tier table before it becomes immutable. */
public final class PriceRuleTierValidator {
    private static final BigDecimal ZERO = BigDecimal.ZERO;

    private PriceRuleTierValidator() { }

    public static void validate(List<PriceRuleTier> supplied) {
        if (supplied == null || supplied.isEmpty()) throw new IllegalArgumentException("tiers are required");
        List<PriceRuleTier> tiers = supplied.stream().sorted(Comparator.comparing(PriceRuleTier::tierNo)).toList();
        BigDecimal expectedMinimum = ZERO;
        for (int index = 0; index < tiers.size(); index++) {
            PriceRuleTier tier = tiers.get(index);
            if (tier.tierNo() != index + 1 || tier.minWeight() == null || tier.minWeight().compareTo(expectedMinimum) != 0) {
                throw new IllegalArgumentException("tier ranges must be contiguous from zero");
            }
            if (tier.maxWeight() != null && tier.maxWeight().compareTo(tier.minWeight()) <= 0) {
                throw new IllegalArgumentException("tier maximum must be greater than its minimum");
            }
            validatePricing(tier);
            if (tier.maxWeight() == null && index != tiers.size() - 1) throw new IllegalArgumentException("only final tier may be unbounded");
            if (tier.maxWeight() != null) expectedMinimum = tier.maxWeight();
        }
        if (tiers.get(tiers.size() - 1).maxWeight() != null) throw new IllegalArgumentException("final tier must be unbounded");
    }

    private static void validatePricing(PriceRuleTier tier) {
        if (tier.billingMode() == PriceRuleTier.BillingMode.FIXED) {
            if (tier.tierFee() == null || tier.tierFee().compareTo(ZERO) < 0 || tier.firstWeight() != null || tier.firstFee() != null || tier.additionalWeight() != null || tier.additionalFee() != null) {
                throw new IllegalArgumentException("fixed tier values are invalid");
            }
            return;
        }
        if (tier.billingMode() != PriceRuleTier.BillingMode.FIRST_CONTINUE || positive(tier.firstWeight()) == false || nonNegative(tier.firstFee()) == false || positive(tier.additionalWeight()) == false || nonNegative(tier.additionalFee()) == false || tier.tierFee() != null) {
            throw new IllegalArgumentException("first-continue tier values are invalid");
        }
    }

    private static boolean positive(BigDecimal value) { return value != null && value.compareTo(ZERO) > 0; }
    private static boolean nonNegative(BigDecimal value) { return value != null && value.compareTo(ZERO) >= 0; }
}
