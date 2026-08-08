package com.shipflow.security.jwt;

public record AccessTokenPrincipal(String subject, Scope scope, String tenantId) {

    public AccessTokenPrincipal {
        if (subject == null || subject.isBlank()) {
            throw new IllegalArgumentException("Access Token subject is required");
        }
        if (scope == null) {
            throw new IllegalArgumentException("Access Token scope is required");
        }
        if (scope == Scope.TENANT && (tenantId == null || tenantId.isBlank())) {
            throw new IllegalArgumentException("Tenant Access Token requires tenant_id");
        }
        if (scope == Scope.PLATFORM && tenantId != null) {
            throw new IllegalArgumentException("Platform Access Token must not contain tenant_id");
        }
    }

    public enum Scope {
        PLATFORM,
        TENANT
    }
}
