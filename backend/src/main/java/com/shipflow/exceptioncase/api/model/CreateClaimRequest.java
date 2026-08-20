package com.shipflow.exceptioncase.api.model;
import jakarta.validation.constraints.*;
import java.math.BigDecimal;
public record CreateClaimRequest(@NotNull @DecimalMin("0.01") @Digits(integer=16,fraction=2) BigDecimal claimAmount,
                                 @NotBlank @Pattern(regexp="^[A-Z]{3}$") String currency,
                                 @NotBlank @Size(max=1000) String claimReason,
                                 @NotNull @PositiveOrZero Long exceptionVersion,
                                 java.util.List<@Positive Long> evidenceAttachmentIds) {
    public CreateClaimRequest(BigDecimal claimAmount, String currency) {
        this(claimAmount, currency, "Legacy claim request", 0L, java.util.List.of());
    }
}
