package com.shipflow.quote.mapper;

import com.shipflow.logistics.domain.model.PriceRuleTier;
import com.shipflow.logistics.domain.model.PublishedPriceRule;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Immutable projection of the rule selected for one new quote. */
public record QuotePricingRule(Long id, Long channelId, int versionNo, String ruleName, String currency,
                               BigDecimal volumeDivisor, PublishedPriceRule.RoundingMode roundingMode,
                               BigDecimal roundingIncrement, LocalDateTime effectiveFrom,
                               List<PriceRuleTier> tiers) {
    public QuotePricingRule {
        tiers = List.copyOf(tiers);
    }

    public PublishedPriceRule publishedRule() {
        return new PublishedPriceRule(id, channelId, versionNo, ruleName, currency, volumeDivisor,
                roundingMode, roundingIncrement, effectiveFrom, tiers);
    }
}
