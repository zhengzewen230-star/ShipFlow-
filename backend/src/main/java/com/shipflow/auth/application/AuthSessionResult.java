package com.shipflow.auth.application;

import com.shipflow.auth.application.model.LoginResult;
import com.shipflow.security.refresh.RefreshToken;

public final class AuthSessionResult {
    private final LoginResult loginResult;
    private final RefreshToken refreshToken;

    public AuthSessionResult(LoginResult loginResult, RefreshToken refreshToken) {
        this.loginResult = loginResult;
        this.refreshToken = refreshToken;
    }
    public LoginResult loginResult() { return loginResult; }
    public RefreshToken refreshToken() { return refreshToken; }
    @Override public String toString() { return "AuthSessionResult{loginResult=" + loginResult + ", refreshToken=redacted}"; }
}
