package com.shipflow.security.refresh;

import java.util.Objects;

/** Opaque raw Refresh Token. Its value is intentionally absent from toString. */
public final class RefreshToken {

    private final String value;

    public RefreshToken(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Refresh Token value is required");
        }
        this.value = value;
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "RefreshToken{redacted=true}";
    }

    @Override
    public boolean equals(Object other) {
        return this == other || other instanceof RefreshToken token && value.equals(token.value);
    }

    @Override
    public int hashCode() {
        return Objects.hash(value);
    }
}
