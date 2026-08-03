package com.shipflow.auth.api.model;

import com.shipflow.auth.model.LoginIdentity;

import java.util.List;

public record CurrentUserResponse(String userId, String username, String displayName, String scope,
                                  String tenantId, List<String> permissions) {
    public static CurrentUserResponse from(LoginIdentity identity) {
        return new CurrentUserResponse(identity.userId().toString(), identity.username(), identity.displayName(),
                identity.scope().name(), identity.tenantId() == null ? null : identity.tenantId().toString(),
                List.copyOf(identity.permissionCodes()));
    }
}
