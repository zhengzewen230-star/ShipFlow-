package com.shipflow.exceptioncase.api.model;
import jakarta.validation.constraints.*;
import java.time.OffsetDateTime;
import java.math.BigDecimal;
public record ClaimResultRequest(@NotBlank String status,
                                 @NotBlank @Size(max=1000) String reason,
                                 @NotNull OffsetDateTime resolvedAt,
                                 @DecimalMin("0.00") @Digits(integer=16,fraction=2) BigDecimal approvedAmount,
                                 @NotNull @PositiveOrZero Long version) {
    public ClaimResultRequest(String status, String reason, OffsetDateTime resolvedAt, Long version) {
        this(status, reason, resolvedAt, null, version);
    }
}
