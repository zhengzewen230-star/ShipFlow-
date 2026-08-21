package com.shipflow.user.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public record UpdateUserRequest(@NotBlank @Size(max=128) String displayName, @NotNull Long version) {}
