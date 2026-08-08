package com.shipflow.auth.service;

import com.shipflow.auth.config.AuthSecurityProperties;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/** Performs one safe PasswordEncoder check without owning user lookup or token issuance. */
@Service
@Profile("!test")
public final class PasswordAuthenticationService {

    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    @Autowired
    public PasswordAuthenticationService(PasswordEncoder passwordEncoder, AuthSecurityProperties properties) {
        this(passwordEncoder, properties.getDummyPasswordHash());
    }

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
        boolean matches = passwordEncoder.matches(candidate, hash);
        if (!matches) {
            throw new LoginIdentityAuthenticationException();
        }
    }
}
