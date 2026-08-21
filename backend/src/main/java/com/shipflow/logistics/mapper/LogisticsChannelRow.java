package com.shipflow.logistics.mapper;

import com.shipflow.logistics.domain.model.LogisticsChannel.TransportMode;
import java.time.LocalDateTime;

/** Mapper projection; service countries are loaded separately to avoid N+1 in list queries. */
public record LogisticsChannelRow(Long id, Long providerId, String channelCode, String channelName,
                                  TransportMode transportMode, String serviceArea, String status,
                                  long version, LocalDateTime createdAt, LocalDateTime updatedAt) { }
