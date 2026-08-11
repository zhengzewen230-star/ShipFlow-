package com.shipflow.exceptioncase.api.model;
import jakarta.validation.constraints.*;
public record AssignExceptionRequest(@NotNull @Positive Long assignedToUserId,
                                     @Size(max=1000) String reason,
                                     @NotNull @PositiveOrZero Long version) { }
