package com.shipflow.auth.model;

/** Internal lookup result; the password hash never leaves the authentication application layer. */
public final class LoginIdentityLookup {

    private final LoginIdentity identity;
    private final String passwordHash;

    public LoginIdentityLookup(LoginIdentity identity, String passwordHash) {
        this.identity = identity;
        this.passwordHash = passwordHash;
    }

    public LoginIdentity identity() {
        return identity;
    }

    public String passwordHash() {
        return passwordHash;
    }

    @Override
    public String toString() {
        return "LoginIdentityLookup{identity=" + identity + '}';
    }
}
