package com.shipflow.auth.application.model;

/** Internal login command; it is not an HTTP request DTO. */
public final class LoginCommand {

    private final String username;
    private final String password;
    private final String tenantCode;

    public LoginCommand(String username, String password, String tenantCode) {
        this.username = username;
        this.password = password;
        this.tenantCode = tenantCode;
    }

    public String username() {
        return username;
    }

    public String password() {
        return password;
    }

    public String tenantCode() {
        return tenantCode;
    }

    @Override
    public String toString() {
        return "LoginCommand{username='" + username + "', tenantCode='" + tenantCode + "'}";
    }
}
