package com.shipflow.security.jwt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;
import com.nimbusds.jose.JOSEObjectType;
import com.nimbusds.jose.proc.DefaultJOSEObjectTypeVerifier;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;

import java.time.Clock;

@Configuration
@EnableConfigurationProperties(JwtProperties.class)
public class JwtCryptoConfiguration {

    @Bean
    @ConditionalOnProperty(prefix = "shipflow.security.jwt", name = "enabled", havingValue = "true")
    JwtKeyMaterial jwtKeyMaterial(JwtProperties properties, PemKeyLoader pemKeyLoader) {
        return pemKeyLoader.load(properties);
    }

    @Bean
    @ConditionalOnProperty(prefix = "shipflow.security.jwt", name = "enabled", havingValue = "true")
    JwtEncoder jwtEncoder(JwtKeyMaterial keyMaterial, JwtProperties properties) {
        RSAKey signingKey = new RSAKey.Builder(keyMaterial.publicKey())
                .privateKey(keyMaterial.privateKey())
                .keyID(properties.getActiveKid())
                .build();
        return new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(signingKey)));
    }

    @Bean
    @ConditionalOnProperty(prefix = "shipflow.security.jwt", name = "enabled", havingValue = "true")
    JwtDecoder jwtDecoder(JwtKeyMaterial keyMaterial, JwtProperties properties, Clock clock) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keyMaterial.publicKey())
                .signatureAlgorithm(SignatureAlgorithm.RS256)
                .validateType(false)
                .jwtProcessorCustomizer(processor -> processor.setJWSTypeVerifier(
                        new DefaultJOSEObjectTypeVerifier<>(new JOSEObjectType("at+jwt"))))
                .build();

        JwtTimestampValidator timestampValidator = new JwtTimestampValidator(properties.getClockSkew());
        timestampValidator.setClock(clock);
        OAuth2TokenValidator<Jwt> validator = new org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator<>(
                timestampValidator,
                new JwtIssuerValidator(properties.getIssuer()),
                new AudienceValidator(properties.getAudience()),
                new HeaderValidator(properties.getActiveKid()));
        decoder.setJwtValidator(validator);
        return decoder;
    }

    private record AudienceValidator(String expectedAudience) implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            return token.getAudience().contains(expectedAudience)
                    ? OAuth2TokenValidatorResult.success()
                    : OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid JWT audience", null));
        }
    }

    private record HeaderValidator(String expectedKid) implements OAuth2TokenValidator<Jwt> {
        @Override
        public OAuth2TokenValidatorResult validate(Jwt token) {
            Object typ = token.getHeaders().get("typ");
            Object kid = token.getHeaders().get("kid");
            if (!"at+jwt".equals(typ)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid JWT typ", null));
            }
            if (!expectedKid.equals(kid)) {
                return OAuth2TokenValidatorResult.failure(new OAuth2Error("invalid_token", "Invalid JWT kid", null));
            }
            return OAuth2TokenValidatorResult.success();
        }
    }
}
