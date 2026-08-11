package com.shipflow.quote.api.model;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;

/** Request measurements use kg and cm as frozen by the quote contract. */
public record CreateQuoteRequest(
        @NotNull Long storeId,
        @NotNull Long channelId,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal declaredWeight,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal declaredLength,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal declaredWidth,
        @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal declaredHeight,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String destinationCountry) {
}
