package com.shipflow.store.api.model;

import java.time.LocalDateTime;

/** Tenant-scoped list projection. It never exposes the platform account secret. */
public record StoreListItem(
        Long id,
        Long tenantId,
        String storeCode,
        String storeName,
        String platformCode,
        String countryRegion,
        String defaultShippingAddress,
        String defaultLogisticsChannel,
        String status,
        long version,
        LocalDateTime updatedAt) { }
