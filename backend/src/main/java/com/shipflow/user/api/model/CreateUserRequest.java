package com.shipflow.user.api.model;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public record CreateUserRequest(@NotBlank @Size(max=128) String username,
                                @NotBlank @Size(max=128) String displayName,
                                @NotBlank @Size(min=12, max=128) String temporaryPassword,
                                @NotEmpty List<@NotNull Long> roleIds) {}
