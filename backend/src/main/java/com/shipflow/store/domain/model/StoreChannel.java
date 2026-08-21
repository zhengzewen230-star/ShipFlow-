package com.shipflow.store.domain.model;

import java.time.LocalDateTime;

public record StoreChannel(
        Long id, Long tenantId, Long storeId, Long channelId,
        String channelCode, String channelName, String providerName,
        String status, boolean isDefault, long version,
        Long createdBy, Long updatedBy, LocalDateTime createdAt,
        LocalDateTime updatedAt) {
}
