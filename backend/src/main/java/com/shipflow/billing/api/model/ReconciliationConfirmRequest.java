package com.shipflow.billing.api.model;
import jakarta.validation.constraints.*;
public record ReconciliationConfirmRequest(@NotBlank @Size(max=64) String resolutionType,
                                          @NotBlank @Size(max=1000) String remark,
                                          @NotNull @PositiveOrZero Long version) { }
