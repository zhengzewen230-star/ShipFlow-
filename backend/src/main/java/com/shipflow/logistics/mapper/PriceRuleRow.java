package com.shipflow.logistics.mapper;
import com.shipflow.logistics.domain.model.PublishedPriceRule.RoundingMode; import java.math.BigDecimal; import java.time.LocalDateTime;
public record PriceRuleRow(Long id, Long channelId, int versionNo, String ruleName, String currency, BigDecimal volumeDivisor, RoundingMode roundingMode, BigDecimal roundingIncrement, LocalDateTime effectiveFrom) { }
