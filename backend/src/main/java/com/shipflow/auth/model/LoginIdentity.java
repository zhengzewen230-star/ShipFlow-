package com.shipflow.auth.model;

import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;

/** Sanitized identity returned after account, password and authority checks. */
public record LoginIdentity(
        Long userId,
        Long tenantId,
        String username,
        String displayName,
        Scope scope,
        Set<String> permissionCodes) {

    public LoginIdentity {
        if (userId == null || username == null || displayName == null || scope == null) {
            throw new IllegalArgumentException("Login identity fields are required");
        }
        if (scope == Scope.TENANT && tenantId == null) {
            throw new IllegalArgumentException("Tenant login identity requires tenant_id");
        }
        if (scope == Scope.PLATFORM && tenantId != null) {
            throw new IllegalArgumentException("Platform login identity must not contain tenant_id");
        }
        permissionCodes = Collections.unmodifiableSet(new LinkedHashSet<>(permissionCodes));
    }

    public enum Scope {
        PLATFORM,
        TENANT
    }
}
