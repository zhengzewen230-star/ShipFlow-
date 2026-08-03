package com.shipflow.auth.service;

import org.springframework.security.crypto.password.PasswordEncoder;

/** Performs one safe PasswordEncoder check without owning user lookup or token issuance. */
public final class PasswordAuthenticationService {

    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public PasswordAuthenticationService(PasswordEncoder passwordEncoder, String dummyPasswordHash) {
        this.passwordEncoder = passwordEncoder;
        if (dummyPasswordHash == null || dummyPasswordHash.isBlank()) {
            throw new IllegalArgumentException("Dummy password hash is required");
        }
        this.dummyPasswordHash = dummyPasswordHash;
    }

    public void authenticate(String rawPassword, String storedPasswordHash) {
        String candidate = rawPassword == null || rawPassword.isBlank() ? "\u0000" : rawPassword;
        String hash = storedPasswordHash == null || storedPasswordHash.isBlank()
                ? dummyPasswordHash
                : storedPasswordHash;
        boolean matches;
        try {
            matches = passwordEncoder.matches(candidate, hash);
        } catch (RuntimeException exception) {
            throw new LoginIdentityAuthenticationException();
        }
        if (!matches) {
            throw new LoginIdentityAuthenticationException();
        }
    }
}
