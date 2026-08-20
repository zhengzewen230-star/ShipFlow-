package com.shipflow.exceptioncase.api.model;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

public record FinanceConfirmRequest(@Size(max = 1000) String reason,
                                    @NotNull @PositiveOrZero Long version) { }
