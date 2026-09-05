package com.shipflow.billing.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record ReconciliationActionRequest(@NotBlank @Size(max=1000) String remark,
                                           @NotNull @PositiveOrZero Long version) { }
