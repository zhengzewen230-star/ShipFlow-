package com.shipflow.tenant.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateTenantRequest(@NotBlank @Size(max = 128) String tenantName, @NotNull Long version) {
}
