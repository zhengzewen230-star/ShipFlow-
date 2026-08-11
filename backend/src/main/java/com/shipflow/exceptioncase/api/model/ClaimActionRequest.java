package com.shipflow.exceptioncase.api.model;
import jakarta.validation.constraints.*;
public record ClaimActionRequest(@Size(max=1000) String reason,
                                 @NotNull @PositiveOrZero Long version) { }
