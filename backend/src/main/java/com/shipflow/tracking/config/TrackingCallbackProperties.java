package com.shipflow.tracking.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;
import java.util.ArrayList;
import java.util.Base64;
import java.util.List;

@ConfigurationProperties(prefix = "shipflow.tracking.callback")
public class TrackingCallbackProperties {
    private Duration maxClockSkew = Duration.ofSeconds(300);
    private List<Credential> credentials = new ArrayList<>();

    public Duration getMaxClockSkew() {
        return maxClockSkew;
    }

    public void setMaxClockSkew(Duration maxClockSkew) {
        this.maxClockSkew = maxClockSkew;
    }

    public List<Credential> getCredentials() {
        return credentials;
    }

    public void setCredentials(List<Credential> credentials) {
        this.credentials = credentials == null ? new ArrayList<>() : new ArrayList<>(credentials);
    }

    public Credential credential(String providerCode) {
        return credentials.stream()
                .filter(value -> value.getProviderCode() != null && value.getProviderCode().equals(providerCode))
                .findFirst()
                .orElse(null);
    }

    public static class Credential {
        private String providerCode;
        private Long systemUserId;
        private String hmacKey;

        public String getProviderCode() {
            return providerCode;
        }

        public void setProviderCode(String providerCode) {
            this.providerCode = providerCode;
        }

        public Long getSystemUserId() {
            return systemUserId;
        }

        public void setSystemUserId(Long systemUserId) {
            this.systemUserId = systemUserId;
        }

        public String getHmacKey() {
            return hmacKey;
        }

        public void setHmacKey(String hmacKey) {
            this.hmacKey = hmacKey;
        }

        public byte[] decodedKey() {
            byte[] decoded = Base64.getDecoder().decode(hmacKey == null ? "" : hmacKey);
            if (decoded.length < 32) {
                throw new IllegalArgumentException("Tracking callback HMAC key must decode to at least 32 bytes");
            }
            return decoded;
        }
    }
}
