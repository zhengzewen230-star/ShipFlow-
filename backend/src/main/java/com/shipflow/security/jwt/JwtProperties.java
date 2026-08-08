package com.shipflow.security.jwt;

import jakarta.annotation.PostConstruct;
import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

/** Minimal configuration for JWT issuance and validation; key material is never stored here. */
@Validated
@ConfigurationProperties(prefix = "shipflow.security.jwt")
public class JwtProperties {

    private boolean enabled;

    @NotBlank
    private String issuer;

    @NotBlank
    private String audience;

    private Duration accessTokenTtl = Duration.ofMinutes(15);

    private Duration clockSkew = Duration.ofSeconds(60);

    @NotBlank
    private String activeKid;

    private String privateKeyLocation;

    private String publicKeyLocation;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getIssuer() {
        return issuer;
    }

    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public Duration getAccessTokenTtl() {
        return accessTokenTtl;
    }

    public void setAccessTokenTtl(Duration accessTokenTtl) {
        this.accessTokenTtl = accessTokenTtl;
    }

    public Duration getClockSkew() {
        return clockSkew;
    }

    public void setClockSkew(Duration clockSkew) {
        this.clockSkew = clockSkew;
    }

    public String getActiveKid() {
        return activeKid;
    }

    public void setActiveKid(String activeKid) {
        this.activeKid = activeKid;
    }

    public String getPrivateKeyLocation() {
        return privateKeyLocation;
    }

    public void setPrivateKeyLocation(String privateKeyLocation) {
        this.privateKeyLocation = privateKeyLocation;
    }

    public String getPublicKeyLocation() {
        return publicKeyLocation;
    }

    public void setPublicKeyLocation(String publicKeyLocation) {
        this.publicKeyLocation = publicKeyLocation;
    }

    public void validate() {
        if (isBlank(issuer) || isBlank(audience) || isBlank(activeKid)) {
            throw new IllegalArgumentException("JWT issuer, audience and activeKid are required");
        }
        if (accessTokenTtl == null || accessTokenTtl.isZero() || accessTokenTtl.isNegative()
                || accessTokenTtl.compareTo(Duration.ofMinutes(15)) > 0) {
            throw new IllegalArgumentException("JWT accessTokenTtl must be between 0 and 15 minutes");
        }
        if (clockSkew == null || clockSkew.isNegative()
                || clockSkew.compareTo(Duration.ofSeconds(60)) > 0) {
            throw new IllegalArgumentException("JWT clockSkew must not exceed 60 seconds");
        }
        if (enabled && (isBlank(privateKeyLocation) || isBlank(publicKeyLocation))) {
            throw new IllegalArgumentException("JWT key locations are required when JWT is enabled");
        }
    }

    @PostConstruct
    void validateConfiguration() {
        validate();
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
