package com.shipflow.auth;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.UUID;

/** Inserts isolated, transaction-scoped auth data without relying on init_data.sql. */
final class AuthIntegrationFixture {

    static final String TEST_PASSWORD = "integration-test-password";

    private final JdbcTemplate jdbcTemplate;
    private final PasswordEncoder passwordEncoder;
    private final String suffix = UUID.randomUUID().toString().replace("-", "");

    AuthIntegrationFixture(JdbcTemplate jdbcTemplate, PasswordEncoder passwordEncoder) {
        this.jdbcTemplate = jdbcTemplate;
        this.passwordEncoder = passwordEncoder;
    }

    Fixture tenantFixture() {
        String tenantCode = "IT_TENANT_" + suffix;
        Long tenantId = insert("INSERT INTO tenant (tenant_code, tenant_name, status) VALUES (?, ?, 'ACTIVE')",
                tenantCode, "Integration Tenant " + suffix);
        String username = "it_user_" + suffix;
        Long userId = insert("INSERT INTO sys_user (tenant_id, username, display_name, password_hash, status) "
                        + "VALUES (?, ?, ?, ?, 'ACTIVE')",
                tenantId, username, "Integration User", passwordEncoder.encode(TEST_PASSWORD));
        String permissionCode = "it:read:" + suffix;
        Long permissionId = insert("INSERT INTO sys_permission (permission_code, permission_name) VALUES (?, ?)",
                permissionCode, "Integration Read");
        Long roleId = insert("INSERT INTO sys_role (tenant_id, role_code, role_name, role_scope, status) "
                        + "VALUES (?, ?, ?, 'TENANT', 'ACTIVE')",
                tenantId, "IT_ROLE_" + suffix, "Integration Role");
        jdbcTemplate.update("INSERT INTO sys_user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)",
                tenantId, userId, roleId);
        jdbcTemplate.update("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                roleId, permissionId);
        return new Fixture(tenantId, tenantCode, username, userId, roleId, permissionId, permissionCode,
                passwordEncoder.encode("dummy-integration-only"));
    }

    Fixture platformFixture() {
        String username = "it_platform_" + suffix;
        Long userId = insert("INSERT INTO sys_user (tenant_id, username, display_name, password_hash, status) "
                        + "VALUES (NULL, ?, ?, ?, 'ACTIVE')",
                username, "Integration Platform User", passwordEncoder.encode(TEST_PASSWORD));
        String permissionCode = "it:platform:read:" + suffix;
        Long permissionId = insert("INSERT INTO sys_permission (permission_code, permission_name) VALUES (?, ?)",
                permissionCode, "Integration Platform Read");
        Long roleId = insert("INSERT INTO sys_role (tenant_id, role_code, role_name, role_scope, status) "
                        + "VALUES (NULL, ?, ?, 'PLATFORM', 'ACTIVE')",
                "IT_PLATFORM_ROLE_" + suffix, "Integration Platform Role");
        jdbcTemplate.update("INSERT INTO sys_user_role (tenant_id, user_id, role_id) VALUES (NULL, ?, ?)",
                userId, roleId);
        jdbcTemplate.update("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                roleId, permissionId);
        return new Fixture(null, null, username, userId, roleId, permissionId, permissionCode,
                passwordEncoder.encode("dummy-integration-only"));
    }

    Fixture mockFixture() {
        String username = "it_mock_" + suffix;
        Long userId = insert("INSERT INTO sys_user (tenant_id, username, display_name, password_hash, status) "
                        + "VALUES (NULL, ?, ?, ?, 'ACTIVE')",
                username, "Integration Mock Account", passwordEncoder.encode(TEST_PASSWORD));
        String permissionCode = "tracking:callback:" + suffix;
        Long permissionId = insert("INSERT INTO sys_permission (permission_code, permission_name) VALUES (?, ?)",
                permissionCode, "Integration Callback");
        Long roleId = insert("INSERT INTO sys_role (tenant_id, role_code, role_name, role_scope, status) "
                        + "VALUES (NULL, ?, ?, 'PLATFORM', 'ACTIVE')",
                "MOCK_LOGISTICS_SYSTEM_" + suffix,
                "Integration Mock Role");
        jdbcTemplate.update("INSERT INTO sys_user_role (tenant_id, user_id, role_id) VALUES (NULL, ?, ?)",
                userId, roleId);
        jdbcTemplate.update("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                roleId, permissionId);
        return new Fixture(null, null, username, userId, roleId, permissionId, permissionCode,
                passwordEncoder.encode("dummy-integration-only"));
    }

    Long addActiveTenantRole(Fixture fixture, String permissionCode) {
        Long permissionId = insert("INSERT INTO sys_permission (permission_code, permission_name) VALUES (?, ?)",
                permissionCode + ":" + suffix, "Integration Extra Permission");
        Long roleId = insert("INSERT INTO sys_role (tenant_id, role_code, role_name, role_scope, status) "
                        + "VALUES (?, ?, ?, 'TENANT', 'ACTIVE')",
                fixture.tenantId(), "IT_EXTRA_ROLE_" + suffix, "Integration Extra Role");
        jdbcTemplate.update("INSERT INTO sys_user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)",
                fixture.tenantId(), fixture.userId(), roleId);
        jdbcTemplate.update("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                roleId, permissionId);
        return roleId;
    }

    Long addActiveTenantRoleWithExistingPermission(Fixture fixture) {
        Long roleId = insert("INSERT INTO sys_role (tenant_id, role_code, role_name, role_scope, status) "
                        + "VALUES (?, ?, ?, 'TENANT', 'ACTIVE')",
                fixture.tenantId(), "IT_DUP_ROLE_" + suffix, "Integration Duplicate Role");
        jdbcTemplate.update("INSERT INTO sys_user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)",
                fixture.tenantId(), fixture.userId(), roleId);
        jdbcTemplate.update("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                roleId, fixture.permissionId());
        return roleId;
    }

    Long addRole(Fixture fixture, String roleScope, String status, Long roleTenantId) {
        return insert("INSERT INTO sys_role (tenant_id, role_code, role_name, role_scope, status) "
                        + "VALUES (?, ?, ?, ?, ?)",
                roleTenantId, "IT_ROLE_" + suffix + "_" + status + "_" + roleScope,
                "Integration State Role", roleScope, status);
    }

    void assignRole(Fixture fixture, Long roleId, Long relationTenantId) {
        jdbcTemplate.update("INSERT INTO sys_user_role (tenant_id, user_id, role_id) VALUES (?, ?, ?)",
                relationTenantId, fixture.userId(), roleId);
    }

    void addPermission(Long roleId, String permissionCode) {
        Long permissionId = insert("INSERT INTO sys_permission (permission_code, permission_name) VALUES (?, ?)",
                permissionCode + ":" + suffix, "Integration Permission");
        jdbcTemplate.update("INSERT INTO sys_role_permission (role_id, permission_id) VALUES (?, ?)",
                roleId, permissionId);
    }

    Long addUser(Fixture fixture, String username, String status) {
        return insert("INSERT INTO sys_user (tenant_id, username, display_name, password_hash, status) "
                        + "VALUES (?, ?, ?, ?, ?)",
                fixture.tenantId(), username + "_" + suffix, "Integration State User",
                passwordEncoder.encode(TEST_PASSWORD), status);
    }

    private Long insert(String sql, Object... args) {
        jdbcTemplate.update(sql, args);
        return jdbcTemplate.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    record Fixture(Long tenantId, String tenantCode, String username, Long userId,
                   Long roleId, Long permissionId, String permissionCode, String dummyPasswordHash) {
    }
}
