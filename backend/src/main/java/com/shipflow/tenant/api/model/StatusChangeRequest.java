package com.shipflow.tenant.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record StatusChangeRequest(@NotBlank String status, @NotNull Long version) {
}
