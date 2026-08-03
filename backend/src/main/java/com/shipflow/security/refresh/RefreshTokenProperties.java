package com.shipflow.security.refresh;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.Base64;

/** Configuration for opaque Refresh Token hashing. The key is Base64-encoded and never logged. */
@Validated
@ConfigurationProperties(prefix = "shipflow.security.refresh-token")
public class RefreshTokenProperties {

    private boolean enabled;

    private String hmacKey = "";

    private Duration familyTtl = Duration.ofDays(30);

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getHmacKey() {
        return hmacKey;
    }

    public void setHmacKey(String hmacKey) {
        this.hmacKey = hmacKey;
    }

    public Duration getFamilyTtl() {
        return familyTtl;
    }

    public void setFamilyTtl(Duration familyTtl) {
        this.familyTtl = familyTtl;
    }

    public void validate() {
        if (familyTtl == null || familyTtl.isNegative() || familyTtl.isZero()) {
            throw new IllegalArgumentException("Refresh Token familyTtl must be positive");
        }
        if (!familyTtl.equals(Duration.ofDays(30))) {
            throw new IllegalArgumentException("Refresh Token familyTtl must be exactly 30 days");
        }
        if (enabled) {
            decodeKey();
        }
    }

    byte[] decodeKey() {
        if (hmacKey == null || hmacKey.isBlank()) {
            throw new IllegalArgumentException("Refresh Token HMAC key is required when enabled");
        }
        final byte[] decoded;
        try {
            decoded = Base64.getDecoder().decode(hmacKey);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Refresh Token HMAC key must be valid Base64", exception);
        }
        if (decoded.length < 32) {
            throw new IllegalArgumentException("Refresh Token HMAC key must decode to at least 32 bytes");
        }
        return decoded.clone();
    }
}
