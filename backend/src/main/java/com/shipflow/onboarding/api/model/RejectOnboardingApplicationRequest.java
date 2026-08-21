package com.shipflow.onboarding.api.model;

import jakarta.validation.constraints.*;

public record RejectOnboardingApplicationRequest(@NotBlank @Size(max = 500) String reviewRemark,
                                                 @NotNull @Min(0) Long version) {
}
