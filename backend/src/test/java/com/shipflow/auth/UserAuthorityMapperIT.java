package com.shipflow.auth;

import com.shipflow.auth.mapper.UserAuthorityMapper;
import com.shipflow.auth.model.UserAuthorityView;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class UserAuthorityMapperIT {

    @Autowired
    private UserAuthorityMapper userAuthorityMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Test
    void activeRoleJoinReturnsMappedRowsAndExcludesWrongScopeAndDisabledRole() {
        AuthIntegrationFixture fixture = new AuthIntegrationFixture(jdbcTemplate, new BCryptPasswordEncoder());
        AuthIntegrationFixture.Fixture tenant = fixture.tenantFixture();
        fixture.addActiveTenantRoleWithExistingPermission(tenant);
        Long disabledRole = fixture.addRole(tenant, "TENANT", "DISABLED", tenant.tenantId());
        fixture.assignRole(tenant, disabledRole, tenant.tenantId());
        Long platformRole = fixture.addRole(tenant, "PLATFORM", "ACTIVE", null);
        fixture.assignRole(tenant, platformRole, tenant.tenantId());

        List<UserAuthorityView> rows = userAuthorityMapper.findActiveAuthorities(
                tenant.userId(), tenant.tenantId(), "TENANT");

        assertThat(rows).hasSize(2);
        assertThat(rows).allSatisfy(row -> {
            assertThat(row.userId()).isEqualTo(tenant.userId());
            assertThat(row.tenantId()).isEqualTo(tenant.tenantId());
            assertThat(row.roleScope()).isEqualTo("TENANT");
            assertThat(row.roleStatus()).isEqualTo("ACTIVE");
            assertThat(row.permissionCode()).isEqualTo(tenant.permissionCode());
        });
    }

    @Test
    void platformRoleJoinRequiresNullTenantId() {
        AuthIntegrationFixture fixture = new AuthIntegrationFixture(jdbcTemplate, new BCryptPasswordEncoder());
        AuthIntegrationFixture.Fixture platform = fixture.platformFixture();

        List<UserAuthorityView> rows = userAuthorityMapper.findActiveAuthorities(
                platform.userId(), null, "PLATFORM");

        assertThat(rows).singleElement().satisfies(row -> {
            assertThat(row.userId()).isEqualTo(platform.userId());
            assertThat(row.tenantId()).isNull();
            assertThat(row.roleScope()).isEqualTo("PLATFORM");
            assertThat(row.roleStatus()).isEqualTo("ACTIVE");
            assertThat(row.permissionCode()).isEqualTo(platform.permissionCode());
        });
    }
}
