package com.shipflow.security.refresh;

/** One externally safe failure for missing, expired, revoked and replayed Refresh Tokens. */
public final class RefreshTokenAuthenticationException extends RuntimeException {

    public static final String ERROR_CODE = "AUTH-1002";

    public RefreshTokenAuthenticationException() {
        super("Refresh authentication failed");
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
