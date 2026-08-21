package com.shipflow.logistics.domain.model;

import java.time.LocalDateTime;

/** Platform-owned master data; it has no tenant_id by design. */
public record LogisticsProvider(Long id, String providerCode, String providerName, String status,
                                long version, LocalDateTime createdAt, LocalDateTime updatedAt) { }
