package com.shipflow.logistics.api.model;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.NotNull; import jakarta.validation.constraints.Size;
public record UpdateLogisticsProviderRequest(@NotBlank @Size(max=128) String providerName, @NotBlank String status, @NotNull Long version) { }
