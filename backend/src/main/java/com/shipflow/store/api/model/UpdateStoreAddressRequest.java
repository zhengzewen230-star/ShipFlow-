package com.shipflow.store.api.model;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record UpdateStoreAddressRequest(
        @NotBlank @Size(max = 64) String addressCode,
        @NotBlank @Size(max = 128) String contactName,
        @Size(max = 128) String companyName,
        @NotBlank @Size(max = 64) String phone,
        @Email @Size(max = 128) String email,
        @NotBlank @Pattern(regexp = "[A-Z]{2}") String countryCode,
        @Size(max = 128) String stateProvince,
        @NotBlank @Size(max = 128) String city,
        @Size(max = 128) String district,
        @NotBlank @Size(max = 255) String addressLine1,
        @Size(max = 255) String addressLine2,
        @NotBlank @Size(max = 32) String postalCode,
        @NotNull Long version) {
}
