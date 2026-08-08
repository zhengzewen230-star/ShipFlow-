package com.shipflow.auth;

import com.shipflow.auth.mapper.SysUserMapper;
import com.shipflow.auth.mapper.UserAuthorityMapper;
import com.shipflow.auth.model.LoginCredentials;
import com.shipflow.auth.model.LoginIdentity;
import com.shipflow.auth.service.LoginIdentityAuthenticationException;
import com.shipflow.auth.service.LoginIdentityService;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class LoginIdentityServiceIT {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private UserAuthorityMapper userAuthorityMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void tenantLoginLoadsAndDeduplicatesRealDatabaseAuthorities() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthIntegrationFixture fixture = new AuthIntegrationFixture(jdbcTemplate, encoder);
        AuthIntegrationFixture.Fixture tenant = fixture.tenantFixture();
        fixture.addActiveTenantRoleWithExistingPermission(tenant);
        LoginIdentityService service = new LoginIdentityService(
                sysUserMapper, userAuthorityMapper, encoder, tenant.dummyPasswordHash());

        LoginIdentity identity = service.authenticate(new LoginCredentials(
                tenant.username(), AuthIntegrationFixture.TEST_PASSWORD, tenant.tenantCode()));

        assertThat(identity.userId()).isEqualTo(tenant.userId());
        assertThat(identity.tenantId()).isEqualTo(tenant.tenantId());
        assertThat(identity.scope()).isEqualTo(LoginIdentity.Scope.TENANT);
        assertThat(identity.permissionCodes()).containsExactly(tenant.permissionCode());
    }

    @Test
    void disabledUserAndDisabledTenantCannotLogin() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthIntegrationFixture fixture = new AuthIntegrationFixture(jdbcTemplate, encoder);
        AuthIntegrationFixture.Fixture tenant = fixture.tenantFixture();
        fixture.addUser(tenant, "disabled_user", "DISABLED");
        LoginIdentityService service = new LoginIdentityService(
                sysUserMapper, userAuthorityMapper, encoder, tenant.dummyPasswordHash());

        assertFailure(service, new LoginCredentials("disabled_user_" + suffixFrom(tenant.username()),
                AuthIntegrationFixture.TEST_PASSWORD, tenant.tenantCode()));

        jdbcTemplate.update("UPDATE tenant SET status = 'DISABLED' WHERE id = ?", tenant.tenantId());
        assertFailure(service, new LoginCredentials(tenant.username(),
                AuthIntegrationFixture.TEST_PASSWORD, tenant.tenantCode()));
    }

    @Test
    void mockLogisticsAccountCannotLoginWithPassword() {
        PasswordEncoder encoder = new BCryptPasswordEncoder();
        AuthIntegrationFixture fixture = new AuthIntegrationFixture(jdbcTemplate, encoder);
        AuthIntegrationFixture.Fixture mock = fixture.mockFixture();
        LoginIdentityService service = new LoginIdentityService(
                sysUserMapper, userAuthorityMapper, encoder, mock.dummyPasswordHash());

        assertFailure(service, new LoginCredentials(mock.username(),
                AuthIntegrationFixture.TEST_PASSWORD, null));
    }

    @Test
    void missingUserStillExecutesDummyBcryptMatch() {
        BCryptPasswordEncoder realEncoder = new BCryptPasswordEncoder();
        PasswordEncoder spyEncoder = Mockito.spy(realEncoder);
        String dummyHash = realEncoder.encode("dummy-integration-only");
        LoginIdentityService service = new LoginIdentityService(
                sysUserMapper, userAuthorityMapper, spyEncoder, dummyHash);

        assertFailure(service, new LoginCredentials("missing_" + System.nanoTime(),
                AuthIntegrationFixture.TEST_PASSWORD, null));

        verify(spyEncoder).matches(AuthIntegrationFixture.TEST_PASSWORD, dummyHash);
    }

    private void assertFailure(LoginIdentityService service, LoginCredentials credentials) {
        assertThatThrownBy(() -> service.authenticate(credentials))
                .isInstanceOf(LoginIdentityAuthenticationException.class)
                .hasMessage("Authentication failed");
    }

    private String suffixFrom(String username) {
        return username.substring(username.lastIndexOf('_') + 1);
    }
}
