package com.shipflow.onboarding.api.model;

import jakarta.validation.constraints.*;

public record CreateOnboardingApplicationRequest(
        @NotBlank @Size(max = 128) String companyName,
        @NotBlank @Size(max = 128) String contactName,
        @NotBlank @Email @Size(max = 254) String businessEmail,
        @NotBlank @Size(max = 64) String contactPhone,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode) {
    @Override public String toString() { return "CreateOnboardingApplicationRequest{companyName='" + companyName + "', contactName='" + contactName + "', businessEmail='[redacted]', contactPhone='[redacted]', countryCode='" + countryCode + "'}"; }
}
