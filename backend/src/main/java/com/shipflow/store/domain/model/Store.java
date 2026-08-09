package com.shipflow.store.domain.model;
import java.time.LocalDateTime;
public record Store(Long id, Long tenantId, String storeCode, String storeName, String platformCode, String platformAccount, String status, long version, LocalDateTime createdAt, LocalDateTime updatedAt) {}
