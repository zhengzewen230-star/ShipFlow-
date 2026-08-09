package com.shipflow.user.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

public record UserStatusChangeRequest(@NotBlank String status, @NotNull Long version) {}
