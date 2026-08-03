package com.shipflow.security.refresh;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Random;
import java.security.SecureRandom;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class RefreshTokenSecurityTest {

    @Test
    void generatedTokenDecodesTo32BytesAndHasUrlSafeNoPaddingEncoding() {
        RefreshToken token = new RefreshTokenGenerator(new SecureRandom()).generate();

        assertThat(Base64.getUrlDecoder().decode(token.value())).hasSize(32);
        assertThat(token.value()).matches("[A-Za-z0-9_-]+").doesNotContain("=");
    }

    @Test
    void consecutiveTokensAreNotEqual() {
        RefreshTokenGenerator generator = new RefreshTokenGenerator(new SecureRandom());

        assertThat(generator.generate()).isNotEqualTo(generator.generate());
    }

    @Test
    void sameTokenHasSameHmacAndDifferentTokenHasDifferentHmac() {
        RefreshTokenProperties properties = propertiesWithKey();
        RefreshTokenHmacService service = new RefreshTokenHmacService(properties);
        RefreshToken first = new RefreshToken("fixed-token-value");
        RefreshToken second = new RefreshToken("different-token-value");

        assertThat(service.digest(first).value()).isEqualTo(service.digest(first).value());
        assertThat(service.digest(first).value()).isNotEqualTo(service.digest(second).value());
        assertThat(service.digest(first).value()).matches("[0-9a-f]{64}");
    }

    @Test
    void hmacConfigurationRejectsMissingInvalidAndShortKeys() {
        RefreshTokenProperties missing = new RefreshTokenProperties();
        assertThatThrownBy(() -> new RefreshTokenHmacService(missing))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("HMAC key");

        RefreshTokenProperties invalid = new RefreshTokenProperties();
        invalid.setHmacKey("not-base64!!");
        assertThatThrownBy(() -> new RefreshTokenHmacService(invalid))
                .isInstanceOf(IllegalArgumentException.class);

        RefreshTokenProperties shortKey = new RefreshTokenProperties();
        shortKey.setHmacKey(Base64.getEncoder().encodeToString(new byte[31]));
        assertThatThrownBy(() -> new RefreshTokenHmacService(shortKey))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("at least 32 bytes");
    }

    @Test
    void enabledConfigurationRejectsOverlongFamilyTtl() {
        RefreshTokenProperties properties = propertiesWithKey();
        properties.setEnabled(true);
        properties.setFamilyTtl(Duration.ofDays(31));

        assertThatThrownBy(properties::validate)
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("30 days");
    }

    @Test
    void sensitiveRefreshTypesAreRedacted() {
        RefreshToken token = new RefreshToken("local-only-token");
        RefreshTokenHash hash = new RefreshTokenHmacService(propertiesWithKey()).digest(token);
        RefreshSessionDO session = new RefreshSessionDO(1L, 2L, null, hash.value(), "family-secret",
                null, "ACTIVE", java.time.LocalDateTime.now(), null,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        RefreshTokenSessionResult result = new RefreshTokenSessionResult(token, 1L, "family-secret", 2L,
                null, java.time.Instant.now());

        assertThat(token.toString()).doesNotContain(token.value());
        assertThat(hash.toString()).doesNotContain(hash.value());
        assertThat(session.toString()).doesNotContain(hash.value(), "family-secret");
        assertThat(result.toString()).doesNotContain(token.value(), "family-secret", hash.value());
        assertThat(new RefreshTokenAuthenticationException().getMessage())
                .doesNotContain(token.value(), hash.value(), "family-secret");
    }

    private RefreshTokenProperties propertiesWithKey() {
        RefreshTokenProperties properties = new RefreshTokenProperties();
        properties.setHmacKey(Base64.getEncoder().encodeToString("local-test-only-refresh-key-32-bytes".getBytes(StandardCharsets.UTF_8)));
        return properties;
    }
}
