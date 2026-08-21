package com.shipflow.logistics.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** A published rule is intentionally immutable; later prices get a new version_no. */
public record PublishedPriceRule(Long id, Long channelId, int versionNo, String ruleName, String currency,
                                 BigDecimal volumeDivisor, RoundingMode roundingMode, BigDecimal roundingIncrement,
                                 LocalDateTime effectiveFrom, List<PriceRuleTier> tiers) {
    public enum RoundingMode { CEILING, ROUND, NONE }
    public PublishedPriceRule { tiers = List.copyOf(tiers); }
}
