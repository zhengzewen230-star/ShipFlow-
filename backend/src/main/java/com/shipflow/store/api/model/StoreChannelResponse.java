package com.shipflow.store.api.model;

import java.time.LocalDateTime;

public record StoreChannelResponse(
        Long id, Long tenantId, Long storeId, Long channelId,
        String channelCode, String channelName, String providerName,
        String status, boolean isDefault, long version, Long createdBy, Long updatedBy,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
}
