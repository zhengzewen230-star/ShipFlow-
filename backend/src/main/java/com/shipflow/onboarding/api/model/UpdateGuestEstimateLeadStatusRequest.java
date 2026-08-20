package com.shipflow.onboarding.api.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateGuestEstimateLeadStatusRequest(
        @NotNull GuestEstimateLeadStatus status,
        @Size(max = 500) String handlingRemark,
        @NotNull Long version) {
}
