package com.shipflow.auth.api.model;

import com.shipflow.auth.application.model.LoginResult;

public record AuthTokenResponse(String accessToken, String tokenType, long expiresIn,
                                String userId, String tenantId, String scope) {
    public static AuthTokenResponse from(LoginResult result) {
        var identity = result.identity();
        return new AuthTokenResponse(result.accessToken(), result.tokenType(), result.expiresIn(),
                identity.userId().toString(), identity.tenantId() == null ? null : identity.tenantId().toString(),
                identity.scope().name());
    }
}
