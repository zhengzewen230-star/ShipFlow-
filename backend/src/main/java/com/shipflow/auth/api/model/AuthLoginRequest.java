package com.shipflow.auth.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AuthLoginRequest(
        @NotBlank @Size(max = 128) String username,
        @NotBlank @Size(max = 256) String password,
        @Size(max = 64) String tenantCode) {
}
