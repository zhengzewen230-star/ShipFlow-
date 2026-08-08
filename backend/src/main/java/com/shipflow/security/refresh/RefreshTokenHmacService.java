package com.shipflow.security.refresh;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;

/** Computes the only persisted representation of a raw Refresh Token. */
public final class RefreshTokenHmacService {

    private final byte[] key;

    public RefreshTokenHmacService(RefreshTokenProperties properties) {
        this.key = properties.decodeKey();
    }

    public RefreshTokenHash digest(RefreshToken token) {
        if (token == null) {
            throw new IllegalArgumentException("Refresh Token is required");
        }
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal(token.value().getBytes(StandardCharsets.UTF_8));
            return RefreshTokenHash.fromBytes(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("Refresh Token HMAC operation failed", exception);
        }
    }
}
