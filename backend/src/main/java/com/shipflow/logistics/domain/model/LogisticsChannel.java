package com.shipflow.logistics.domain.model;

import java.time.LocalDateTime;
import java.util.List;

/** Platform channel. Tenant callers can only consume an eligible ACTIVE instance. */
public record LogisticsChannel(Long id, Long providerId, String channelCode, String channelName,
                               TransportMode transportMode, String serviceArea, String status,
                               long version, List<String> serviceCountries,
                               LocalDateTime createdAt, LocalDateTime updatedAt) {
    public enum TransportMode { OCEAN, AIR, ROAD, RAIL, COURIER }
    public LogisticsChannel { serviceCountries = serviceCountries == null ? List.of() : List.copyOf(serviceCountries); }
}
