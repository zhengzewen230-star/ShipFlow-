package com.shipflow.tenant.api.model;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.Pattern;

public record CreateTenantRequest(
        @NotBlank @Size(max = 64) String tenantCode,
        @NotBlank @Size(max = 128) String tenantName,
        @NotNull @Valid InitialAdminRequest initialAdmin) {
    public record InitialAdminRequest(
            @NotBlank @Size(max = 128) String username,
            @NotBlank @Size(max = 128) String displayName,
            @NotBlank @Size(min = 12, max = 128)
            @Pattern(regexp = "^(?=.*[A-Z])(?=.*[a-z])(?=.*[0-9])(?=.*[^A-Za-z0-9]).+$")
            String temporaryPassword) {
        @Override
        public String toString() {
            return "InitialAdminRequest{username='" + username + "', displayName='" + displayName + "'}";
        }
    }

    @Override
    public String toString() {
        return "CreateTenantRequest{tenantCode='" + tenantCode + "', tenantName='" + tenantName
                + "', initialAdmin=" + initialAdmin + "}";
    }
}
