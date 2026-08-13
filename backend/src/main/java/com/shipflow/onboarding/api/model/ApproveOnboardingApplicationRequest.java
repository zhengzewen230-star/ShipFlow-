package com.shipflow.onboarding.api.model;

import jakarta.validation.constraints.*;

public record ApproveOnboardingApplicationRequest(
        @NotBlank @Size(max = 64) String tenantCode,
        @NotBlank @Size(max = 128) String adminUsername,
        @NotBlank @Size(max = 500) String reviewRemark,
        @NotNull @Min(0) Long version) {
}
