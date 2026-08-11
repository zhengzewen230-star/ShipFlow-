package com.shipflow.exceptioncase.api.model;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
public record CreateExceptionRequest(@NotBlank String exceptionType,
                                     @NotBlank @Size(max=1000) String description,
                                     @NotNull OffsetDateTime reportedAt,
                                     Long trackingEventId) { }
