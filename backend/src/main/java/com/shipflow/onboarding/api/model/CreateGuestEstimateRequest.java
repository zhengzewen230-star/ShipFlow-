package com.shipflow.onboarding.api.model;

import jakarta.validation.constraints.*;
import java.math.BigDecimal;

/** Public lead only; it deliberately contains no tenant, store, channel or price-rule fields. */
public record CreateGuestEstimateRequest(
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String originCountry,
        @NotBlank @Pattern(regexp = "^[A-Z]{2}$") String destinationCountry,
        @NotBlank @Pattern(regexp = "^(OCEAN|AIR|ROAD|RAIL|COURIER)$") String transportMode,
        @NotNull GuestCargoType cargoType,
        @NotBlank @Size(min = 2, max = 80) String cargoName,
        @NotNull @DecimalMin(value = "0.001") @Digits(integer = 12, fraction = 3) BigDecimal weight,
        @NotNull @DecimalMin(value = "0.000001") @Digits(integer = 12, fraction = 6) BigDecimal volume,
        @NotBlank @Size(max = 128) String contactName,
        @NotBlank @Email @Size(max = 254) String businessEmail,
        @NotBlank @Size(max = 64) String contactPhone) {
    @Override public String toString() { return "CreateGuestEstimateRequest{originCountry='" + originCountry + "', destinationCountry='" + destinationCountry + "', transportMode='" + transportMode + "', cargoType='" + cargoType + "', cargoName='" + cargoName + "', contactName='" + contactName + "', businessEmail='[redacted]', contactPhone='[redacted]'}"; }
}
