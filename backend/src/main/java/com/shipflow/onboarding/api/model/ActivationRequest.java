package com.shipflow.onboarding.api.model;

import jakarta.validation.constraints.*;

public record ActivationRequest(@NotBlank @Size(min = 32, max = 256) String invitationToken,
                                @NotBlank @Size(min = 12, max = 128)
                                @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).+$") String password) {
    @Override public String toString() { return "ActivationRequest{invitationToken='[redacted]', password='[redacted]'}"; }
}
