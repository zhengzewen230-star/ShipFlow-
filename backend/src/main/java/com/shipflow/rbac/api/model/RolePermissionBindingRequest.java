package com.shipflow.rbac.api.model;

import jakarta.validation.constraints.NotNull;
import java.util.List;

public record RolePermissionBindingRequest(@NotNull List<@NotNull Long> permissionIds, @NotNull Long version) {}
