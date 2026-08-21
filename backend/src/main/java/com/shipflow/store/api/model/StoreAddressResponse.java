package com.shipflow.store.api.model;

import java.time.LocalDateTime;

public record StoreAddressResponse(
        Long id, Long tenantId, Long storeId, String addressCode,
        String contactName, String companyName, String phone, String email,
        String countryCode, String stateProvince, String city, String district,
        String addressLine1, String addressLine2, String postalCode,
        String status, boolean isDefault, long version,
        LocalDateTime createdAt, LocalDateTime updatedAt) {
}
