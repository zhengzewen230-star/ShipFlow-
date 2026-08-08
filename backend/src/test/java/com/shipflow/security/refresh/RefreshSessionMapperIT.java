package com.shipflow.security.refresh;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class RefreshSessionMapperIT {

    @Autowired
    private RefreshSessionMapper mapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void insertLookupRotateAndRevokeFamilyInOneTransaction() {
        Fixture fixture = fixture();
        RefreshTokenHmacService hmac = testHmacService();
        RefreshTokenGenerator generator = new RefreshTokenGenerator(new SecureRandom());
        LocalDateTime now = LocalDateTime.now().withNano(0);
        RefreshSessionDO first = session(fixture.userId(), fixture.tenantId(),
                hmac.digest(generator.generate()).value(), fixture.familyId(), null, now.plusDays(30), now);

        assertThat(mapper.insert(first)).isEqualTo(1);
        assertThat(first.id()).isNotNull();
        assertThat(mapper.findByTokenHashForUpdate(first.tokenHash()).id()).isEqualTo(first.id());
        assertThat(mapper.markActiveAsRotated(first.id(), now)).isEqualTo(1);

        RefreshSessionDO second = session(fixture.userId(), fixture.tenantId(),
                hmac.digest(generator.generate()).value(), fixture.familyId(), first.id(), now.plusDays(30), now);
        mapper.insert(second);
        assertThat(mapper.findFamily(fixture.familyId())).hasSize(2)
                .extracting(RefreshSessionDO::previousSessionId)
                .containsExactly(null, first.id());

        assertThat(mapper.revokeFamily(fixture.familyId(), now)).isEqualTo(2);
        assertThat(mapper.findFamily(fixture.familyId()))
                .extracting(RefreshSessionDO::status)
                .containsOnly("REVOKED");
    }

    @Test
    void rollbackLeavesNoTestSessionBehind() {
        Fixture fixture = fixture();
        RefreshTokenHmacService hmac = testHmacService();
        RefreshTokenGenerator generator = new RefreshTokenGenerator(new SecureRandom());
        LocalDateTime now = LocalDateTime.now().withNano(0);
        RefreshSessionDO session = session(fixture.userId(), fixture.tenantId(),
                hmac.digest(generator.generate()).value(), fixture.familyId(), null, now.plusDays(30), now);
        mapper.insert(session);

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM auth_refresh_session WHERE family_id = ?", Integer.class,
                fixture.familyId())).isEqualTo(1);
        // @Transactional rolls this test's inserts back after completion.
    }

    private Fixture fixture() {
        String suffix = UUID.randomUUID().toString().replace("-", "");
        Long tenantId = insert("INSERT INTO tenant (tenant_code, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                "IT_REFRESH_" + suffix, "Refresh Integration Tenant");
        Long userId = insert("INSERT INTO sys_user (tenant_id, username, display_name, password_hash, status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                tenantId, "it_refresh_" + suffix, "Refresh Integration User", "$2a$10$dummy");
        return new Fixture(tenantId, userId, UUID.randomUUID().toString());
    }

    private RefreshSessionDO session(Long userId, Long tenantId, String hash, String familyId,
                                     Long previousId, LocalDateTime expiresAt, LocalDateTime now) {
        return new RefreshSessionDO(null, userId, tenantId, hash, familyId, previousId,
                "ACTIVE", expiresAt, null, now, now);
    }

    private RefreshTokenHmacService testHmacService() {
        byte[] key = new byte[32];
        new SecureRandom().nextBytes(key);
        RefreshTokenProperties properties = new RefreshTokenProperties();
        properties.setHmacKey(Base64.getEncoder().encodeToString(key));
        return new RefreshTokenHmacService(properties);
    }

    private Long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private record Fixture(Long tenantId, Long userId, String familyId) {
    }
}
