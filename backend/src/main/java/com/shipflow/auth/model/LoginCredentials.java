package com.shipflow.auth.model;

/** Internal login input; deliberately has no toString so the password cannot be logged accidentally. */
public final class LoginCredentials {

    private final String username;
    private final String password;
    private final String tenantCode;

    public LoginCredentials(String username, String password, String tenantCode) {
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
        return "LoginCredentials{" +
                "username='" + username + '\'' +
                ", tenantCode='" + tenantCode + '\'' +
                '}';
    }
}
