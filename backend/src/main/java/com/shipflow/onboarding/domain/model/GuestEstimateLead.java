package com.shipflow.onboarding.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record GuestEstimateLead(
        Long id, String referenceNo, String originCountry, String destinationCountry,
        String transportMode, String cargoType, String cargoName,
        BigDecimal weight, BigDecimal volume, String contactName, String businessEmail,
        String contactPhone, String status, String handlingRemark, Long handledByUserId,
        LocalDateTime handledAt, long version, LocalDateTime createdAt, LocalDateTime updatedAt) {
}
