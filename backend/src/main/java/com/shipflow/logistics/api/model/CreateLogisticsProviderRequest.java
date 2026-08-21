package com.shipflow.logistics.api.model;
import jakarta.validation.constraints.NotBlank; import jakarta.validation.constraints.Size;
public record CreateLogisticsProviderRequest(@NotBlank @Size(max=64) String providerCode, @NotBlank @Size(max=128) String providerName) { }
