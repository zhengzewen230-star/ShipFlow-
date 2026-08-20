package com.shipflow.logistics.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/** Deliberately limited tenant-facing projection of platform channel master data. */
public record PublicLogisticsChannel(
        Long id,
        String providerName,
        String channelCode,
        String channelName,
        LogisticsChannel.TransportMode transportMode,
        List<String> serviceCountries,
        Integer priceRuleVersion,
        LocalDateTime effectiveFrom,
        LocalDateTime effectiveTo,
        BigDecimal volumeDivisor,
        String status,
        List<String> unavailableFields) {
    public PublicLogisticsChannel {
        serviceCountries = List.copyOf(serviceCountries);
        unavailableFields = List.copyOf(unavailableFields);
    }
}
