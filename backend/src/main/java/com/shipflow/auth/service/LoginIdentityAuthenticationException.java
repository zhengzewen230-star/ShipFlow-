package com.shipflow.auth.service;

/** Internal authentication failure with one externally safe error code. */
public class LoginIdentityAuthenticationException extends RuntimeException {

    public static final String ERROR_CODE = "AUTH-1001";

    public LoginIdentityAuthenticationException() {
        super("Authentication failed");
    }

    public String getErrorCode() {
        return ERROR_CODE;
    }
}
