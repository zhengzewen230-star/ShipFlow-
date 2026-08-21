package com.shipflow.store.api.model;

import java.time.LocalDateTime;
import java.util.List;

/** 店铺只读详情；平台账号只允许以脱敏值返回。 */
public record StoreDetailResponse(
        Long id,
        Long tenantId,
        String storeCode,
        String storeName,
        String platformCode,
        String platformAccountMasked,
        String countryRegion,
        String defaultShippingAddress,
        String defaultLogisticsChannel,
        String status,
        long version,
        LocalDateTime createdAt,
        LocalDateTime updatedAt,
        long historicalOrderCount,
        List<StoreAuditSummary> auditSummary,
        List<String> unavailableFields,
        long configurationVersion) { }
