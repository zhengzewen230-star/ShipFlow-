package com.shipflow.exceptioncase.api.model;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
public record ClaimResultRequest(@NotBlank String status,
                                 @NotBlank @Size(max=1000) String reason,
                                 @NotNull OffsetDateTime resolvedAt,
                                 @NotNull @PositiveOrZero Long version) { }
