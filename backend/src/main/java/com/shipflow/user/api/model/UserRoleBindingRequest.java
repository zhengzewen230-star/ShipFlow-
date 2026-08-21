package com.shipflow.user.api.model;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record UserRoleBindingRequest(@NotNull List<@NotNull Long> roleIds, @NotNull Long version) {}
