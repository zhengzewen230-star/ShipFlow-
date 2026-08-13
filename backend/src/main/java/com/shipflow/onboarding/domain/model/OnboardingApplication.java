package com.shipflow.onboarding.domain.model;

import java.time.LocalDateTime;

public record OnboardingApplication(Long id, String applicationNo, String companyName, String contactName,
                                    String businessEmail, String contactPhone, String countryCode, String status,
                                    String reviewRemark, Long tenantId, Long initialUserId, long version,
                                    LocalDateTime createdAt, LocalDateTime reviewedAt) {
}
