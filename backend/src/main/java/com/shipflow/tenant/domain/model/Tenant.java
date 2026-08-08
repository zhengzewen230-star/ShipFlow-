package com.shipflow.tenant.domain.model;

import java.time.LocalDateTime;

public record Tenant(Long id, String tenantCode, String tenantName, String status,
                     long version, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
