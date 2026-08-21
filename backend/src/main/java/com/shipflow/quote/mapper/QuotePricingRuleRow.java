package com.shipflow.quote.mapper;

import com.shipflow.logistics.domain.model.PublishedPriceRule;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record QuotePricingRuleRow(Long id, Long channelId, int versionNo, String ruleName, String currency,
                                  BigDecimal volumeDivisor, PublishedPriceRule.RoundingMode roundingMode,
                                  BigDecimal roundingIncrement, LocalDateTime effectiveFrom) {
}
