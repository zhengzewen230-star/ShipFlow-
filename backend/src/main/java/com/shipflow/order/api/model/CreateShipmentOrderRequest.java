package com.shipflow.order.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.util.List;

/** Package measurements and fees are deliberately copied from the accepted quote. */
public record CreateShipmentOrderRequest(@NotNull @Valid Address senderAddress,
                                         @NotNull @Valid Address receiverAddress,
                                         @NotEmpty List<@Valid Item> items) {
    public record Address(@NotBlank String contactName, @NotBlank String phone,
                          String companyName, String email,
                          @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
                          String stateProvince, @NotBlank String city, String district,
                          @NotBlank String addressLine1, String addressLine2,
                          @NotBlank String postalCode) { }
    public record Item(@NotBlank String sku, @NotBlank String productName,
                       @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal quantity,
                       @NotNull @DecimalMin(value = "0", inclusive = false) BigDecimal unitPrice,
                       @NotBlank String currency, String countryOfOrigin) { }
}
