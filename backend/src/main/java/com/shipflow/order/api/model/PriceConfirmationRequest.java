package com.shipflow.order.api.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record PriceConfirmationRequest(
        @NotNull Long feeAdjustmentId,
        @NotNull @DecimalMin(value = "0.00") BigDecimal expectedFee,
        @NotNull @Min(0) Long version) {
}
