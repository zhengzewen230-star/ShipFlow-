package com.shipflow.auth;

import com.shipflow.auth.mapper.SysUserMapper;
import com.shipflow.auth.model.SysUserDO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class SysUserMapperIT {

    @Autowired
    private SysUserMapper sysUserMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void platformAndTenantQueriesUseTheirOwnExactScope() {
        AuthIntegrationFixture fixture = new AuthIntegrationFixture(jdbcTemplate, new BCryptPasswordEncoder());
        AuthIntegrationFixture.Fixture platform = fixture.platformFixture();
        AuthIntegrationFixture.Fixture tenant = fixture.tenantFixture();

        SysUserDO platformUser = sysUserMapper.findPlatformUser(platform.username());
        SysUserDO tenantUser = sysUserMapper.findTenantUser(tenant.tenantCode(), tenant.username());

        assertThat(platformUser.id()).isEqualTo(platform.userId());
        assertThat(platformUser.tenantId()).isNull();
        assertThat(platformUser.tenantStatus()).isNull();
        assertThat(tenantUser.id()).isEqualTo(tenant.userId());
        assertThat(tenantUser.tenantId()).isEqualTo(tenant.tenantId());
        assertThat(tenantUser.tenantStatus()).isEqualTo("ACTIVE");
        assertThat(tenantUser.passwordHash()).startsWith("$2a$");
    }

    @Test
    void tenantCodeMustMatchAndNeverFallsBackToPlatformScope() {
        AuthIntegrationFixture fixture = new AuthIntegrationFixture(jdbcTemplate, new BCryptPasswordEncoder());
        AuthIntegrationFixture.Fixture platform = fixture.platformFixture();
        AuthIntegrationFixture.Fixture tenant = fixture.tenantFixture();

        assertThat(sysUserMapper.findTenantUser(tenant.tenantCode(), platform.username()))
                .isNull();
        assertThat(sysUserMapper.findTenantUser("IT_UNKNOWN_" + tenant.tenantCode(), tenant.username()))
                .isNull();
        assertThat(sysUserMapper.findPlatformUser(tenant.username())).isNull();
    }
}
