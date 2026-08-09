package com.shipflow.security.jwt;

import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.DefaultResourceLoader;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.security.oauth2.jwt.JwtException;

import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class JwtCryptoTest {

    private final Instant baseTime = Instant.parse("2026-08-03T00:00:00Z");
    private KeyPair keyPair;
    private JwtProperties properties;
    private JwtCryptoConfiguration configuration;
    private Clock clock;
    private JwtEncoder encoder;
    private JwtDecoder decoder;

    @BeforeEach
    void setUp() throws Exception {
        keyPair = generateKeyPair(2048);
        properties = properties(Duration.ofMinutes(15));
        clock = Clock.fixed(baseTime, ZoneOffset.UTC);
        configuration = new JwtCryptoConfiguration();
        JwtKeyMaterial material = new JwtKeyMaterial(
                (java.security.interfaces.RSAPrivateKey) keyPair.getPrivate(),
                (java.security.interfaces.RSAPublicKey) keyPair.getPublic());
        encoder = configuration.jwtEncoder(material, properties);
        decoder = configuration.jwtDecoder(material, properties, clock);
    }

    @Test
    void tenantTokenIsIssuedAndValidated() {
        String token = new AccessTokenService(encoder, properties, clock)
                .issue(new AccessTokenPrincipal("1001", AccessTokenPrincipal.Scope.TENANT, "10"));

        Jwt jwt = decoder.decode(token);

        assertThat(jwt.getSubject()).isEqualTo("1001");
        assertThat(jwt.getClaimAsString("scope")).isEqualTo("TENANT");
        assertThat(jwt.getClaimAsString("tenant_id")).isEqualTo("10");
    }

    @Test
    void platformTokenOmitsTenantId() {
        Jwt jwt = decoder.decode(new AccessTokenService(encoder, properties, clock)
                .issue(new AccessTokenPrincipal("1", AccessTokenPrincipal.Scope.PLATFORM, null)));

        assertThat(jwt.getClaimAsString("scope")).isEqualTo("PLATFORM");
        assertThat(jwt.getClaims()).doesNotContainKey("tenant_id");
    }

    @Test
    void jwtEncoderAndDecoderAreCreatedFromValidatedRsaMaterial() {
        assertThat(encoder).isNotNull();
        assertThat(decoder).isNotNull();

        String token = new AccessTokenService(encoder, properties, clock)
                .issue(new AccessTokenPrincipal("1", AccessTokenPrincipal.Scope.PLATFORM, null));
        assertThat(decoder.decode(token).getSubject()).isEqualTo("1");
    }

    @Test
    void tenantTokenWithoutTenantIdIsRejected() {
        assertThatThrownBy(() -> new AccessTokenPrincipal("1001", AccessTokenPrincipal.Scope.TENANT, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void claimsAndHeadersUseTheFrozenContract() {
        Jwt jwt = decoder.decode(new AccessTokenService(encoder, properties, clock)
                .issue(new AccessTokenPrincipal("1001", AccessTokenPrincipal.Scope.TENANT, "10")));

        assertThat(jwt.getIssuer().toString()).isEqualTo("https://shipflow");
        assertThat(jwt.getAudience()).containsExactly("shipflow-api");
        assertThat(jwt.getHeaders()).containsEntry("kid", "test-kid");
        assertThat(jwt.getHeaders()).containsEntry("typ", "at+jwt");
        assertThat(jwt.getClaims()).doesNotContainKeys("roles", "permissions", "auth_version");
        assertThat(jwt.getId()).isNotBlank();
    }

    @Test
    void eachIssuedTokenHasUniqueJti() {
        AccessTokenService service = new AccessTokenService(encoder, properties, clock);
        Jwt first = decoder.decode(service.issue(new AccessTokenPrincipal("1", AccessTokenPrincipal.Scope.PLATFORM, null)));
        Jwt second = decoder.decode(service.issue(new AccessTokenPrincipal("1", AccessTokenPrincipal.Scope.PLATFORM, null)));

        assertThat(first.getId()).isNotEqualTo(second.getId());
    }

    @Test
    void tokenIsRejectedWhenSignatureIsTampered() {
        String token = new AccessTokenService(encoder, properties, clock)
                .issue(new AccessTokenPrincipal("1", AccessTokenPrincipal.Scope.PLATFORM, null));
        String[] parts = token.split("\\.");
        String signature = parts[2];
        char replacement = signature.charAt(0) == 'a' ? 'b' : 'a';
        parts[2] = replacement + signature.substring(1);
        String tampered = String.join(".", parts);

        assertValidationFailure(tampered);
    }

    @Test
    void tokenIsRejectedWithWrongPublicKey() throws Exception {
        JwtDecoder wrongDecoder = configuration.jwtDecoder(
                new JwtKeyMaterial((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate(),
                        (java.security.interfaces.RSAPublicKey) generateKeyPair(2048).getPublic()),
                properties, clock);
        String token = new AccessTokenService(encoder, properties, clock)
                .issue(new AccessTokenPrincipal("1", AccessTokenPrincipal.Scope.PLATFORM, null));

        assertThatThrownBy(() -> wrongDecoder.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void issuerAndAudienceAreValidated() {
        JwtProperties wrongIssuer = properties(Duration.ofMinutes(15));
        wrongIssuer.setIssuer("https://other-issuer");
        assertValidationFailure(new AccessTokenService(encoder, wrongIssuer, clock)
                .issue(new AccessTokenPrincipal("1", AccessTokenPrincipal.Scope.PLATFORM, null)));

        JwtProperties wrongAudience = properties(Duration.ofMinutes(15));
        wrongAudience.setAudience("other-audience");
        assertValidationFailure(new AccessTokenService(encoder, wrongAudience, clock)
                .issue(new AccessTokenPrincipal("1", AccessTokenPrincipal.Scope.PLATFORM, null)));
    }

    @Test
    void expiredTokenIsRejectedWithControlledClock() {
        JwtProperties shortLived = properties(Duration.ofSeconds(1));
        String token = new AccessTokenService(encoder, shortLived, clock)
                .issue(new AccessTokenPrincipal("1", AccessTokenPrincipal.Scope.PLATFORM, null));
        JwtDecoder afterExpiry = configuration.jwtDecoder(
                new JwtKeyMaterial((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate(),
                        (java.security.interfaces.RSAPublicKey) keyPair.getPublic()),
                shortLived, Clock.fixed(baseTime.plusSeconds(62), ZoneOffset.UTC));

        assertThatThrownBy(() -> afterExpiry.decode(token)).isInstanceOf(JwtException.class);
    }

    @Test
    void notBeforeInFutureIsRejected() {
        JwtClaimsSet claims = claims(baseTime.plusSeconds(120), baseTime.plusSeconds(120), baseTime.plusSeconds(300));
        String token = encoder.encode(JwtEncoderParameters.from(header("test-kid", SignatureAlgorithm.RS256), claims))
                .getTokenValue();

        assertValidationFailure(token);
    }

    @Test
    void nonRs256TokenIsRejected() {
        JwtClaimsSet claims = claims(baseTime, baseTime, baseTime.plusSeconds(900));
        String token = encoder.encode(JwtEncoderParameters.from(header("test-kid", SignatureAlgorithm.RS512), claims))
                .getTokenValue();

        assertValidationFailure(token);
    }

    @Test
    void invalidTypAndKidAreRejected() {
        JwtClaimsSet claims = claims(baseTime, baseTime, baseTime.plusSeconds(900));
        assertValidationFailure(encoder.encode(JwtEncoderParameters.from(header("test-kid", SignatureAlgorithm.RS256, "JWT"), claims))
                .getTokenValue());
        String wrongKidToken = encoder.encode(JwtEncoderParameters.from(header("test-kid", SignatureAlgorithm.RS256), claims))
                .getTokenValue();
        assertValidationFailure(replaceHeaderKid(wrongKidToken, "other-kid"));
    }

    @Test
    void ttlAndClockSkewConfigurationAreBounded() {
        JwtProperties tooLong = properties(Duration.ofSeconds(901));
        assertThatThrownBy(tooLong::validate).isInstanceOf(IllegalArgumentException.class);

        JwtProperties tooMuchSkew = properties(Duration.ofMinutes(15));
        tooMuchSkew.setClockSkew(Duration.ofSeconds(61));
        assertThatThrownBy(tooMuchSkew::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void requiredConfigurationCannotBeBlank() {
        JwtProperties invalid = properties(Duration.ofMinutes(15));
        invalid.setIssuer(" ");
        assertThatThrownBy(invalid::validate).isInstanceOf(IllegalArgumentException.class);

        invalid = properties(Duration.ofMinutes(15));
        invalid.setAudience(" ");
        assertThatThrownBy(invalid::validate).isInstanceOf(IllegalArgumentException.class);

        invalid = properties(Duration.ofMinutes(15));
        invalid.setActiveKid("");
        assertThatThrownBy(invalid::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void disabledConfigurationDoesNotRequireKeyLocations() {
        JwtProperties disabled = properties(Duration.ofMinutes(15));
        disabled.setEnabled(false);
        disabled.setPrivateKeyLocation(null);
        disabled.setPublicKeyLocation(null);

        disabled.validate();
    }

    @Test
    void enabledConfigurationRequiresKeyLocations() {
        JwtProperties invalid = properties(Duration.ofMinutes(15));
        invalid.setEnabled(true);
        invalid.setPrivateKeyLocation("");
        invalid.setPublicKeyLocation("");

        assertThatThrownBy(invalid::validate).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void pemLoaderAcceptsPkcs8PrivateAndX509PublicKeys() {
        PemKeyLoader loader = new PemKeyLoader(new DefaultResourceLoader());

        assertThat(loader.loadPrivateKey(toPem("PRIVATE KEY", keyPair.getPrivate().getEncoded())))
                .isInstanceOf(java.security.interfaces.RSAPrivateKey.class);
        assertThat(loader.loadPublicKey(toPem("PUBLIC KEY", keyPair.getPublic().getEncoded())))
                .isInstanceOf(java.security.interfaces.RSAPublicKey.class);
    }

    @Test
    void rsaKeySmallerThan2048BitsIsRejected() throws Exception {
        KeyPair small = generateKeyPair(1024);
        PemKeyLoader loader = new PemKeyLoader(new DefaultResourceLoader());

        assertThatThrownBy(() -> loader.loadPublicKey(toPem("PUBLIC KEY", small.getPublic().getEncoded())))
                .hasMessageContaining("Unable to load JWT public RSA key")
                .satisfies(error -> assertThat(error.getMessage())
                        .doesNotContain(Base64.getEncoder().encodeToString(small.getPublic().getEncoded())));
    }

    @Test
    void malformedPemFailsWithoutEchoingKeyContents() {
        PemKeyLoader loader = new PemKeyLoader(new DefaultResourceLoader());
        String malformed = "-----BEGIN PUBLIC KEY-----secret-key-material-----END PUBLIC KEY-----";

        assertThatThrownBy(() -> loader.loadPublicKey(malformed))
                .hasMessageContaining("Unable to load JWT public RSA key")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("secret-key-material"));
    }

    @Test
    void wrongPemTypeIsRejected() {
        PemKeyLoader loader = new PemKeyLoader(new DefaultResourceLoader());
        String publicPem = toPem("PUBLIC KEY", keyPair.getPublic().getEncoded());

        assertThatThrownBy(() -> loader.loadPrivateKey(publicPem))
                .hasMessageContaining("Unable to load JWT private RSA key");
    }

    private JwtProperties properties(Duration ttl) {
        JwtProperties result = new JwtProperties();
        result.setEnabled(true);
        result.setIssuer("https://shipflow");
        result.setAudience("shipflow-api");
        result.setAccessTokenTtl(ttl);
        result.setClockSkew(Duration.ofSeconds(60));
        result.setActiveKid("test-kid");
        result.setPrivateKeyLocation("classpath:test-private.pem");
        result.setPublicKeyLocation("classpath:test-public.pem");
        return result;
    }

    private JwtClaimsSet claims(Instant issuedAt, Instant notBefore, Instant expiresAt) {
        return JwtClaimsSet.builder()
                .issuer("https://shipflow")
                .subject("1")
                .audience(List.of("shipflow-api"))
                .issuedAt(issuedAt)
                .notBefore(notBefore)
                .expiresAt(expiresAt)
                .id("test-jti")
                .claim("scope", "PLATFORM")
                .build();
    }

    private JwsHeader header(String kid, SignatureAlgorithm algorithm) {
        return header(kid, algorithm, "at+jwt");
    }

    private JwsHeader header(String kid, SignatureAlgorithm algorithm, String typ) {
        return JwsHeader.with(algorithm).type(typ).keyId(kid).build();
    }

    private void assertValidationFailure(String token) {
        assertThatThrownBy(() -> decoder.decode(token)).isInstanceOf(JwtException.class);
    }

    private String replaceHeaderKid(String token, String kid) {
        String[] parts = token.split("\\.");
        String header = new String(Base64.getUrlDecoder().decode(parts[0]), StandardCharsets.UTF_8)
                .replace("\"kid\":\"test-kid\"", "\"kid\":\"" + kid + "\"");
        return Base64.getUrlEncoder().withoutPadding().encodeToString(header.getBytes(StandardCharsets.UTF_8))
                + "." + parts[1] + "." + parts[2];
    }

    private KeyPair generateKeyPair(int bits) throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(bits);
        return generator.generateKeyPair();
    }

    private String toPem(String type, byte[] encoded) {
        String body = Base64.getMimeEncoder(64, "\n".getBytes(StandardCharsets.US_ASCII)).encodeToString(encoded);
        return "-----BEGIN " + type + "-----\n" + body + "\n-----END " + type + "-----";
    }
}
