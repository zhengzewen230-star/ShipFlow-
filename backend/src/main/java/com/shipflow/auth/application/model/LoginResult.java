package com.shipflow.auth.application.model;

import com.shipflow.auth.model.LoginIdentity;

/** Internal login result; accessToken is intentionally omitted from toString. */
public final class LoginResult {

    private final String accessToken;
    private final String tokenType;
    private final long expiresIn;
    private final LoginIdentity identity;

    public LoginResult(String accessToken, String tokenType, long expiresIn, LoginIdentity identity) {
        this.accessToken = accessToken;
        this.tokenType = tokenType;
        this.expiresIn = expiresIn;
        this.identity = identity;
    }

    public String accessToken() {
        return accessToken;
    }

    public String tokenType() {
        return tokenType;
    }

    public long expiresIn() {
        return expiresIn;
    }

    public LoginIdentity identity() {
        return identity;
    }

    @Override
    public String toString() {
        return "LoginResult{tokenType='" + tokenType + "', expiresIn=" + expiresIn
                + ", identity=" + identity + '}';
    }
}
