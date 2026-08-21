package com.shipflow.security.refresh;

import com.shipflow.IntegrationJwtTestConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.context.annotation.Import;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Base64;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** Real Spring-proxy transaction tests; this class intentionally has no @Transactional test boundary. */
@SpringBootTest
@ActiveProfiles("integration")
@Import({IntegrationJwtTestConfiguration.class, RefreshTokenSessionServiceIT.TestBeans.class})
class RefreshTokenSessionServiceIT {

    @Autowired
    private RefreshTokenSessionService service;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private RepeatableSecureRandom secureRandom;

    private String familyId;
    private Long userId;
    private Long tenantId;

    @Test
    void normalRotationCommitsOldRotatedAndNewActiveWithAbsoluteExpiry() {
        createUser();

        RefreshTokenSessionResult first = service.issueInitial(userId, tenantId);
        familyId = first.familyId();
        RefreshTokenSessionResult second = service.rotate(first.refreshToken());

        assertThat(second.familyId()).isEqualTo(first.familyId());
        assertThat(second.expiresAt()).isEqualTo(first.expiresAt());
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_refresh_session WHERE family_id = ? AND status = 'ROTATED'",
                Integer.class, first.familyId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_refresh_session WHERE family_id = ? AND status = 'ACTIVE'",
                Integer.class, first.familyId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_refresh_session WHERE family_id = ? AND previous_session_id = ?",
                Integer.class, first.familyId(), first.sessionId())).isEqualTo(1);
    }

    @Test
    void replayFailureStillCommitsFamilyRevocationThroughSpringProxy() {
        createUser();

        RefreshTokenSessionResult first = service.issueInitial(userId, tenantId);
        familyId = first.familyId();
        RefreshTokenSessionResult second = service.rotate(first.refreshToken());

        assertThatThrownBy(() -> service.rotate(first.refreshToken()))
                .isInstanceOf(RefreshTokenAuthenticationException.class)
                .hasMessage("Refresh authentication failed");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_refresh_session WHERE family_id = ? AND status = 'REVOKED'",
                Integer.class, second.familyId())).isEqualTo(2);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_refresh_session WHERE family_id = ? AND status = 'ACTIVE'",
                Integer.class, second.familyId())).isEqualTo(0);
    }

    @Test
    void databaseFailureRollsBackRotationAndDoesNotCreateSecondActiveSession() {
        createUser();
        RefreshTokenSessionResult first = service.issueInitial(userId, tenantId);
        familyId = first.familyId();
        secureRandom.repeatNextBytes();

        assertThatThrownBy(() -> service.rotate(first.refreshToken()))
                .isInstanceOf(RuntimeException.class)
                .isNotInstanceOf(RefreshTokenAuthenticationException.class);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_refresh_session WHERE family_id = ? AND status = 'ACTIVE'",
                Integer.class, first.familyId())).isEqualTo(1);
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_refresh_session WHERE family_id = ?",
                Integer.class, first.familyId())).isEqualTo(1);
    }

    @AfterEach
    void cleanup() {
        try {
            if (familyId != null) {
                jdbcTemplate.update(
                        "UPDATE auth_refresh_session SET previous_session_id = NULL WHERE family_id = ?",
                        familyId);
                jdbcTemplate.update("DELETE FROM auth_refresh_session WHERE family_id = ?", familyId);
            }
            if (userId != null) {
                jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", userId);
            }
            if (tenantId != null) {
                jdbcTemplate.update("DELETE FROM tenant WHERE id = ?", tenantId);
            }
        } catch (RuntimeException ignored) {
            // Cleanup must not replace the original test failure with a teardown exception.
        }
    }

    private void createUser() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        tenantId = insert("INSERT INTO tenant (tenant_code, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                "IT_REFRESH_SERVICE_" + suffix, "Refresh Service Integration Tenant");
        userId = insert("INSERT INTO sys_user (tenant_id, username, display_name, password_hash, status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                tenantId, "it_refresh_service_" + suffix, "Refresh Service User", "$2a$10$dummy");
    }

    private Long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    @TestConfiguration(proxyBeanMethods = false)
    static class TestBeans {

        @Bean
        @Primary
        RefreshTokenProperties refreshTokenProperties() {
            RefreshTokenProperties properties = new RefreshTokenProperties();
            properties.setEnabled(true);
            byte[] key = new byte[32];
            new SecureRandom().nextBytes(key);
            properties.setHmacKey(Base64.getEncoder().encodeToString(key));
            properties.setFamilyTtl(Duration.ofDays(30));
            return properties;
        }

        @Bean
        RefreshTokenHmacService refreshTokenHmacService(RefreshTokenProperties properties) {
            return new RefreshTokenHmacService(properties);
        }

        @Bean
        RepeatableSecureRandom repeatableSecureRandom() {
            return new RepeatableSecureRandom();
        }

        @Bean
        RefreshTokenGenerator refreshTokenGenerator(RepeatableSecureRandom random) {
            return new RefreshTokenGenerator(random);
        }

        @Bean
        MyBatisRefreshSessionRepository refreshSessionRepository(RefreshSessionMapper mapper) {
            return new MyBatisRefreshSessionRepository(mapper);
        }

        @Bean
        RefreshTokenSessionService refreshTokenSessionService(
                MyBatisRefreshSessionRepository repository,
                RefreshTokenGenerator generator,
                RefreshTokenHmacService hmac,
                RefreshTokenProperties properties,
                Clock clock) {
            return new RefreshTokenSessionService(repository, generator, hmac, properties, clock);
        }
    }

    static class RepeatableSecureRandom extends SecureRandom {
        private final SecureRandom delegate = new SecureRandom();
        private volatile boolean repeatNext;
        private byte[] previous;

        @Override
        public void nextBytes(byte[] bytes) {
            if (repeatNext && previous != null) {
                System.arraycopy(previous, 0, bytes, 0, bytes.length);
                repeatNext = false;
                return;
            }
            delegate.nextBytes(bytes);
            previous = bytes.clone();
        }

        void repeatNextBytes() {
            repeatNext = true;
        }
    }
}
