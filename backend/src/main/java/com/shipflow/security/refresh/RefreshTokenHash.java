package com.shipflow.security.refresh;

import java.util.HexFormat;

/** Lowercase 64-character HMAC-SHA256 representation, safe to persist but not log. */
public final class RefreshTokenHash {

    private final String value;

    private RefreshTokenHash(String value) {
        this.value = value;
    }

    static RefreshTokenHash fromBytes(byte[] bytes) {
        return new RefreshTokenHash(HexFormat.of().formatHex(bytes));
    }

    public String value() {
        return value;
    }

    @Override
    public String toString() {
        return "RefreshTokenHash{redacted=true}";
    }
}
