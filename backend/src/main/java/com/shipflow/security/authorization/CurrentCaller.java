package com.shipflow.security.authorization;

import com.shipflow.auth.model.LoginIdentity;

import java.util.Set;

/** Authenticated caller snapshot rebuilt from the current database state. */
public record CurrentCaller(
        Long userId,
        Long tenantId,
        LoginIdentity.Scope scope,
        Set<String> permissionCodes) {

    public CurrentCaller {
        if (userId == null || scope == null || permissionCodes == null) {
            throw new IllegalArgumentException("Current caller fields are required");
        }
        permissionCodes = Set.copyOf(permissionCodes);
        if (scope == LoginIdentity.Scope.TENANT && tenantId == null) {
            throw new IllegalArgumentException("Tenant caller requires tenant_id");
        }
        if (scope == LoginIdentity.Scope.PLATFORM && tenantId != null) {
            throw new IllegalArgumentException("Platform caller must not contain tenant_id");
        }
    }
}
