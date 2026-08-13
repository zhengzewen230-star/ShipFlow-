package com.shipflow.tenant;

import com.shipflow.auth.model.LoginCredentials;
import com.shipflow.auth.service.LoginIdentityAuthenticationException;
import com.shipflow.auth.service.LoginIdentityService;
import com.shipflow.security.refresh.RefreshTokenSessionService;
import com.shipflow.tenant.api.model.CreateTenantRequest;
import com.shipflow.tenant.api.model.StatusChangeRequest;
import com.shipflow.tenant.api.model.UpdateTenantRequest;
import com.shipflow.tenant.application.TenantApplicationService;
import com.shipflow.tenant.domain.model.Tenant;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;
import org.springframework.context.annotation.Primary;
import org.springframework.test.context.ActiveProfiles;

import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;

/** Real MySQL coverage. Run only with the integration profile; this class is never part of ordinary test. */
@SpringBootTest
@ActiveProfiles("integration")
@Import(TenantApplicationServiceIT.JwtTestConfiguration.class)
class TenantApplicationServiceIT {
    private static final String PASSWORD = "Tenant-Integration-2026!";

    @Autowired private TenantApplicationService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PasswordEncoder passwordEncoder;
    @Autowired private LoginIdentityService identityService;

    private String tenantCode;
    private String adminUsername;
    private String idempotencyKey;
    private String operatorUsername;
    private Long operatorUserId;

    @TestConfiguration(proxyBeanMethods = false)
    static class JwtTestConfiguration {
        private final java.security.KeyPair keyPair;

        JwtTestConfiguration() {
            try {
                var generator = java.security.KeyPairGenerator.getInstance("RSA");
                generator.initialize(2048);
                this.keyPair = generator.generateKeyPair();
            } catch (java.security.GeneralSecurityException exception) {
                throw new IllegalStateException("Test RSA key creation failed", exception);
            }
        }

        @Bean
        JwtEncoder jwtEncoder() {
            var key = new com.nimbusds.jose.jwk.RSAKey.Builder(
                    (java.security.interfaces.RSAPublicKey) keyPair.getPublic())
                    .privateKey((java.security.interfaces.RSAPrivateKey) keyPair.getPrivate())
                    .keyID("test-kid")
                    .build();
            return new NimbusJwtEncoder(new com.nimbusds.jose.jwk.source.ImmutableJWKSet<>(
                    new com.nimbusds.jose.jwk.JWKSet(key)));
        }

        @Bean
        JwtDecoder jwtDecoder() {
            return NimbusJwtDecoder.withPublicKey(
                    (java.security.interfaces.RSAPublicKey) keyPair.getPublic()).build();
        }

        @Bean
        @Primary
        RefreshTokenSessionService refreshTokenSessionService() {
            return mock(RefreshTokenSessionService.class);
        }
    }

    @BeforeEach
    void createTestOperator() {
        operatorUsername = "IT_TENANT_OPERATOR_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.update("INSERT INTO sys_user (tenant_id, username, display_name, password_hash, status, deleted, version) "
                        + "VALUES (NULL, ?, 'Integration Operator', 'integration-test-hash', 'ACTIVE', 0, 0)",
                operatorUsername);
        operatorUserId = jdbc.queryForObject("SELECT id FROM sys_user WHERE tenant_id IS NULL AND username = ? AND deleted = 0",
                Long.class, operatorUsername);
        assertThat(operatorUserId).isNotNull();
    }

    @AfterEach
    void cleanup() {
        if (idempotencyKey != null) {
            jdbc.update("DELETE FROM api_idempotency_record WHERE scope_tenant_id = 0 AND operation_id = 'createTenant' AND idempotency_key = ?", idempotencyKey);
        }
        if (tenantCode != null) {
            var tenantIds = jdbc.query("SELECT id FROM tenant WHERE tenant_code = ?", (rs, rowNum) -> rs.getLong(1), tenantCode);
            if (!tenantIds.isEmpty()) {
                Long tenantId = tenantIds.get(0);
                jdbc.update("DELETE rp FROM sys_role_permission rp JOIN sys_role r ON r.id = rp.role_id WHERE r.tenant_id = ?", tenantId);
                jdbc.update("DELETE ur FROM sys_user_role ur WHERE ur.tenant_id = ?", tenantId);
                jdbc.update("DELETE FROM audit_log WHERE tenant_id = ?", tenantId);
                jdbc.update("DELETE FROM sys_user WHERE tenant_id = ?", tenantId);
                jdbc.update("DELETE FROM sys_role WHERE tenant_id = ?", tenantId);
                jdbc.update("DELETE FROM tenant WHERE id = ?", tenantId);
            }
        }
        if (operatorUserId != null) {
            jdbc.update("DELETE FROM audit_log WHERE operator_user_id = ?", operatorUserId);
        }
        if (operatorUsername != null) {
            jdbc.update("DELETE a FROM audit_log a JOIN sys_user u ON u.id = a.operator_user_id "
                            + "WHERE u.tenant_id IS NULL AND u.username = ? AND u.deleted = 0", operatorUsername);
            jdbc.update("DELETE FROM sys_user WHERE tenant_id IS NULL AND username = ? AND deleted = 0", operatorUsername);
        }
    }

    @Test
    void createsTenantAdminRoleUserRelationshipPermissionsAndAudit() {
        CreateTenantRequest request = request();
        String key = key();
        idempotencyKey = key;
        Tenant tenant = service.create(request, key, operatorUserId, UUID.randomUUID().toString());

        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM tenant WHERE id = ? AND deleted = 0", Integer.class, tenant.id())).isEqualTo(1);
        Long roleId = jdbc.queryForObject("SELECT id FROM sys_role WHERE tenant_id = ? AND role_code = 'MERCHANT_ADMIN' AND role_scope = 'TENANT' AND deleted = 0", Long.class, tenant.id());
        Long userId = jdbc.queryForObject("SELECT id FROM sys_user WHERE tenant_id = ? AND username = ? AND deleted = 0", Long.class, tenant.id(), adminUsername);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user_role WHERE tenant_id = ? AND user_id = ? AND role_id = ?", Integer.class, tenant.id(), userId, roleId)).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id WHERE rp.role_id=? AND p.permission_code LIKE 'tenant:%'", Integer.class, roleId)).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role_permission rp JOIN sys_permission p ON p.id=rp.permission_id WHERE rp.role_id=?", Integer.class, roleId)).isEqualTo(8);
        String hash = jdbc.queryForObject("SELECT password_hash FROM sys_user WHERE id = ?", String.class, userId);
        assertThat(passwordEncoder.matches(PASSWORD, hash)).isTrue();
        assertThat(hash).doesNotContain(PASSWORD);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM audit_log WHERE tenant_id=? AND resource_type='tenant' AND resource_id=? AND operator_user_id=?", Integer.class, tenant.id(), tenant.id(), operatorUserId)).isEqualTo(1);
    }

    @Test
    void idempotencyReplaysAndDifferentBodyConflicts() {
        CreateTenantRequest request = request();
        String key = key();
        idempotencyKey = key;
        Tenant first = service.create(request, key, operatorUserId, UUID.randomUUID().toString());
        Tenant replay = service.create(request, key, operatorUserId, UUID.randomUUID().toString());
        assertThat(replay.id()).isEqualTo(first.id());
        assertThatThrownBy(() -> service.create(new CreateTenantRequest(request.tenantCode(), "Different", request.initialAdmin()), key, operatorUserId, null))
                .hasMessage("COMMON-1009");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_user WHERE tenant_id=?", Integer.class, first.id())).isEqualTo(1);
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM sys_role WHERE tenant_id=? AND role_code='MERCHANT_ADMIN'", Integer.class, first.id())).isEqualTo(1);
    }

    @Test
    void versionAndStatusChangesArePersistedAndIdentityIsRejectedAfterDisable() {
        CreateTenantRequest request = request();
        String key = key();
        idempotencyKey = key;
        Tenant tenant = service.create(request, key, operatorUserId, null);
        Tenant updated = service.update(tenant.id(), new UpdateTenantRequest("Updated", tenant.version()), operatorUserId, null);
        assertThat(updated.version()).isEqualTo(tenant.version() + 1);
        Tenant disabled = service.changeStatus(tenant.id(), new StatusChangeRequest("DISABLED", updated.version()), operatorUserId, null);
        assertThat(disabled.status()).isEqualTo("DISABLED");
        assertThatThrownBy(() -> identityService.authenticate(new LoginCredentials(adminUsername, PASSWORD, tenantCode)))
                .isInstanceOf(LoginIdentityAuthenticationException.class);
    }

    @Test
    void rejectedCreateBeforePersistenceLeavesNoIdempotencyRecord() {
        CreateTenantRequest request = request();
        String key = key();
        idempotencyKey = key;

        assertThatThrownBy(() -> service.create(request, null, operatorUserId, UUID.randomUUID().toString()))
                .hasMessage("COMMON-1001");
        assertThat(jdbc.queryForObject(
                "SELECT COUNT(*) FROM api_idempotency_record WHERE scope_tenant_id = 0 AND operation_id = 'createTenant' AND idempotency_key = ?",
                Integer.class, key)).isZero();
    }

    private CreateTenantRequest request() {
        tenantCode = "IT_TENANT_" + UUID.randomUUID().toString().replace("-", "").substring(0, 20);
        adminUsername = "admin_" + UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        return new CreateTenantRequest(tenantCode, "Integration Tenant",
                new CreateTenantRequest.InitialAdminRequest(adminUsername, "Integration Admin", PASSWORD));
    }

    private String key() { return "it-key-" + UUID.randomUUID(); }
}
