package com.shipflow.security.refresh;

import java.security.SecureRandom;
import java.util.Base64;

/** Generates 32-byte opaque tokens using URL-safe Base64 without padding. */
public final class RefreshTokenGenerator {

    private static final int TOKEN_BYTES = 32;
    private final SecureRandom secureRandom;

    public RefreshTokenGenerator(SecureRandom secureRandom) {
        this.secureRandom = secureRandom;
    }

    public RefreshToken generate() {
        byte[] bytes = new byte[TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return new RefreshToken(Base64.getUrlEncoder().withoutPadding().encodeToString(bytes));
    }
}
