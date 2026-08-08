package com.shipflow.auth.model;

/** One row of the active user-role-permission join used for authorization loading. */
public record UserAuthorityView(
        Long userId,
        Long tenantId,
        String roleCode,
        String roleScope,
        String roleStatus,
        String permissionCode) {
}
