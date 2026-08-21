package com.shipflow.logistics.mapper;

import com.shipflow.logistics.domain.model.LogisticsChannel.TransportMode;
import java.math.BigDecimal;
import java.time.LocalDateTime;

/** SQL projection for the tenant-visible public catalogue only. */
public record PublicLogisticsChannelRow(
        Long id,
        String providerName,
        String channelCode,
        String channelName,
        TransportMode transportMode,
        String status,
        Integer priceRuleVersion,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        BigDecimal volumeDivisor) {
}
