package com.shipflow.security.jwt;

import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public class AccessTokenService {

    private final JwtEncoder jwtEncoder;
    private final JwtProperties properties;
    private final Clock clock;

    public AccessTokenService(JwtEncoder jwtEncoder, JwtProperties properties, Clock clock) {
        this.jwtEncoder = jwtEncoder;
        this.properties = properties;
        this.clock = clock;
        properties.validate();
    }

    public String issue(AccessTokenPrincipal principal) {
        Instant issuedAt = clock.instant();
        JwtClaimsSet.Builder claims = JwtClaimsSet.builder()
                .issuer(properties.getIssuer())
                .subject(principal.subject())
                .audience(List.of(properties.getAudience()))
                .issuedAt(issuedAt)
                .notBefore(issuedAt)
                .expiresAt(issuedAt.plus(properties.getAccessTokenTtl()))
                .id(UUID.randomUUID().toString())
                .claim("scope", principal.scope().name());
        if (principal.scope() == AccessTokenPrincipal.Scope.TENANT) {
            claims.claim("tenant_id", principal.tenantId());
        }
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256)
                .type("at+jwt")
                .keyId(properties.getActiveKid())
                .build();
        return jwtEncoder.encode(JwtEncoderParameters.from(header, claims.build())).getTokenValue();
    }
}
