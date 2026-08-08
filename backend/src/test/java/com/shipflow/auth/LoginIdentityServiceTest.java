package com.shipflow.auth;

import com.shipflow.auth.mapper.SysUserMapper;
import com.shipflow.auth.mapper.UserAuthorityMapper;
import com.shipflow.auth.model.LoginCredentials;
import com.shipflow.auth.model.LoginIdentity;
import com.shipflow.auth.model.SysUserDO;
import com.shipflow.auth.model.UserAuthorityView;
import com.shipflow.auth.service.LoginIdentityAuthenticationException;
import com.shipflow.auth.service.LoginIdentityService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginIdentityServiceTest {

    private static final String PASSWORD = "local-test-password";

    private SysUserMapper sysUserMapper;
    private UserAuthorityMapper userAuthorityMapper;
    private PasswordEncoder passwordEncoder;
    private String userPasswordHash;
    private String dummyPasswordHash;
    private LoginIdentityService service;

    @BeforeEach
    void setUp() {
        sysUserMapper = Mockito.mock(SysUserMapper.class);
        userAuthorityMapper = Mockito.mock(UserAuthorityMapper.class);
        passwordEncoder = new BCryptPasswordEncoder();
        userPasswordHash = passwordEncoder.encode(PASSWORD);
        dummyPasswordHash = passwordEncoder.encode("dummy-only-test-value");
        service = new LoginIdentityService(sysUserMapper, userAuthorityMapper, passwordEncoder, dummyPasswordHash);
    }

    @Test
    void platformUserLoginSucceeds() {
        SysUserDO user = user(1L, null, null);
        when(sysUserMapper.findPlatformUser("platform_admin")).thenReturn(user);
        when(userAuthorityMapper.findActiveAuthorities(1L, null, "PLATFORM"))
                .thenReturn(List.of(authority(1L, null, "PLATFORM_ADMIN", "PLATFORM", "tenant:create")));

        LoginIdentity identity = service.authenticate(new LoginCredentials("platform_admin", PASSWORD, null));

        assertThat(identity.scope()).isEqualTo(LoginIdentity.Scope.PLATFORM);
        assertThat(identity.tenantId()).isNull();
        assertThat(identity.permissionCodes()).containsExactly("tenant:create");
    }

    @Test
    void tenantUserLoginSucceeds() {
        SysUserDO user = user(2L, 1L, "ACTIVE");
        when(sysUserMapper.findTenantUser("TENANT_DEMO_001", "merchant_admin_001")).thenReturn(user);
        when(userAuthorityMapper.findActiveAuthorities(2L, 1L, "TENANT"))
                .thenReturn(List.of(authority(2L, 1L, "MERCHANT_ADMIN", "TENANT", "order:create")));

        LoginIdentity identity = service.authenticate(
                new LoginCredentials("merchant_admin_001", PASSWORD, "TENANT_DEMO_001"));

        assertThat(identity.scope()).isEqualTo(LoginIdentity.Scope.TENANT);
        assertThat(identity.tenantId()).isEqualTo(1L);
    }

    @Test
    void tenantCodeDoesNotFallbackToPlatformUser() {
        when(sysUserMapper.findTenantUser("TENANT_DEMO_001", "same-name")).thenReturn(null);

        assertFailure(new LoginCredentials("same-name", PASSWORD, "TENANT_DEMO_001"));

        verify(sysUserMapper, never()).findPlatformUser(anyString());
    }

    @Test
    void missingTenantCodeDoesNotSearchTenantUser() {
        when(sysUserMapper.findPlatformUser("same-name")).thenReturn(null);

        assertFailure(new LoginCredentials("same-name", PASSWORD, null));

        verify(sysUserMapper, never()).findTenantUser(anyString(), anyString());
    }

    @Test
    void blankTenantCodeUsesPlatformScope() {
        when(sysUserMapper.findPlatformUser("platform_admin")).thenReturn(user(1L, null, null));
        when(userAuthorityMapper.findActiveAuthorities(1L, null, "PLATFORM"))
                .thenReturn(List.of(authority(1L, null, "PLATFORM_ADMIN", "PLATFORM", "tenant:create")));

        LoginIdentity identity = service.authenticate(new LoginCredentials("platform_admin", PASSWORD, ""));

        assertThat(identity.scope()).isEqualTo(LoginIdentity.Scope.PLATFORM);
        verify(sysUserMapper, never()).findTenantUser(anyString(), anyString());
    }

    @Test
    void whitespaceTenantCodeUsesPlatformScope() {
        when(sysUserMapper.findPlatformUser("platform_admin")).thenReturn(user(1L, null, null));
        when(userAuthorityMapper.findActiveAuthorities(1L, null, "PLATFORM"))
                .thenReturn(List.of(authority(1L, null, "PLATFORM_ADMIN", "PLATFORM", "tenant:create")));

        LoginIdentity identity = service.authenticate(new LoginCredentials("platform_admin", PASSWORD, "  "));

        assertThat(identity.scope()).isEqualTo(LoginIdentity.Scope.PLATFORM);
        verify(sysUserMapper, never()).findTenantUser(anyString(), anyString());
    }

    @Test
    void blankUsernameUsesDummyPathAndDoesNotQuery() {
        assertFailure(new LoginCredentials("", PASSWORD, null));
        verify(sysUserMapper, never()).findPlatformUser(anyString());
        verify(sysUserMapper, never()).findTenantUser(anyString(), anyString());
    }

    @Test
    void whitespaceUsernameUsesDummyPathAndDoesNotQuery() {
        assertFailure(new LoginCredentials("  ", PASSWORD, null));
        verify(sysUserMapper, never()).findPlatformUser(anyString());
        verify(sysUserMapper, never()).findTenantUser(anyString(), anyString());
    }

    @Test
    void missingUserUsesDummyBcryptPathAndUnifiedFailure() {
        PasswordEncoder spyEncoder = Mockito.spy(new BCryptPasswordEncoder());
        service = new LoginIdentityService(sysUserMapper, userAuthorityMapper, spyEncoder, dummyPasswordHash);
        when(sysUserMapper.findPlatformUser("missing")).thenReturn(null);

        assertFailure(new LoginCredentials("missing", PASSWORD, null));

        verify(spyEncoder).matches(PASSWORD, dummyPasswordHash);
    }

    @Test
    void dummyHashIsValidAndUsesSameCostAsTestUserHash() {
        assertThat(passwordEncoder.matches("dummy-only-test-value", dummyPasswordHash)).isTrue();
        assertThat(dummyPasswordHash).matches("\\$2[aby]\\$[0-9]{2}\\$[./A-Za-z0-9]{53}");
        assertThat(dummyPasswordHash.substring(4, 6)).isEqualTo(userPasswordHash.substring(4, 6));
    }

    @Test
    void wrongPasswordUsesUnifiedFailure() {
        when(sysUserMapper.findPlatformUser("platform_admin")).thenReturn(user(1L, null, null));

        assertFailure(new LoginCredentials("platform_admin", "wrong-password", null));
    }

    @Test
    void disabledUserUsesUnifiedFailure() {
        when(sysUserMapper.findPlatformUser("disabled")).thenReturn(user(1L, null, null, "DISABLED"));

        assertFailure(new LoginCredentials("disabled", PASSWORD, null));
    }

    @Test
    void disabledTenantUsesUnifiedFailure() {
        when(sysUserMapper.findTenantUser("DISABLED_TENANT", "merchant")).thenReturn(user(2L, 1L, "DISABLED"));

        assertFailure(new LoginCredentials("merchant", PASSWORD, "DISABLED_TENANT"));
    }

    @Test
    void unknownTenantCodeUsesUnifiedFailure() {
        when(sysUserMapper.findTenantUser("UNKNOWN", "merchant")).thenReturn(null);

        assertFailure(new LoginCredentials("merchant", PASSWORD, "UNKNOWN"));
    }

    @Test
    void noActiveRoleUsesUnifiedFailure() {
        when(sysUserMapper.findPlatformUser("platform_admin")).thenReturn(user(1L, null, null));
        when(userAuthorityMapper.findActiveAuthorities(1L, null, "PLATFORM")).thenReturn(List.of());

        assertFailure(new LoginCredentials("platform_admin", PASSWORD, null));
    }

    @Test
    void disabledRoleUsesUnifiedFailureImmediately() {
        when(sysUserMapper.findPlatformUser("platform_admin")).thenReturn(user(1L, null, null));
        when(userAuthorityMapper.findActiveAuthorities(1L, null, "PLATFORM"))
                .thenReturn(List.of(authority(1L, null, "PLATFORM_ADMIN", "PLATFORM", "tenant:create", "DISABLED")));

        assertFailure(new LoginCredentials("platform_admin", PASSWORD, null));
    }

    @Test
    void roleScopeMismatchUsesUnifiedFailure() {
        when(sysUserMapper.findPlatformUser("platform_admin")).thenReturn(user(1L, null, null));
        when(userAuthorityMapper.findActiveAuthorities(1L, null, "PLATFORM"))
                .thenReturn(List.of(authority(1L, null, "MERCHANT_ADMIN", "TENANT", "order:create")));

        assertFailure(new LoginCredentials("platform_admin", PASSWORD, null));
    }

    @Test
    void crossTenantRoleUsesUnifiedFailure() {
        when(sysUserMapper.findTenantUser("TENANT_DEMO_001", "merchant")).thenReturn(user(2L, 1L, "ACTIVE"));
        when(userAuthorityMapper.findActiveAuthorities(2L, 1L, "TENANT"))
                .thenReturn(List.of(authority(2L, 2L, "MERCHANT_ADMIN", "TENANT", "order:create")));

        assertFailure(new LoginCredentials("merchant", PASSWORD, "TENANT_DEMO_001"));
    }

    @Test
    void mockLogisticsAccountCannotUsePasswordLogin() {
        when(sysUserMapper.findPlatformUser("mock_logistics_callback")).thenReturn(user(10L, null, null));
        when(userAuthorityMapper.findActiveAuthorities(10L, null, "PLATFORM"))
                .thenReturn(List.of(authority(10L, null, "MOCK_LOGISTICS_SYSTEM", "PLATFORM", "tracking:callback")));

        assertFailure(new LoginCredentials("mock_logistics_callback", PASSWORD, null));
    }

    @Test
    void duplicatePermissionsAreDeduplicated() {
        when(sysUserMapper.findTenantUser("TENANT_DEMO_001", "merchant")).thenReturn(user(2L, 1L, "ACTIVE"));
        when(userAuthorityMapper.findActiveAuthorities(2L, 1L, "TENANT")).thenReturn(List.of(
                authority(2L, 1L, "MERCHANT_ADMIN", "TENANT", "order:create"),
                authority(2L, 1L, "MERCHANT_ADMIN", "TENANT", "order:create"),
                authority(2L, 1L, "MERCHANT_ADMIN", "TENANT", "order:operate")));

        LoginIdentity identity = service.authenticate(new LoginCredentials("merchant", PASSWORD, "TENANT_DEMO_001"));

        assertThat(identity.permissionCodes()).containsExactly("order:create", "order:operate");
    }

    @Test
    void activeRoleWithoutPermissionsReturnsEmptyPermissionSet() {
        when(sysUserMapper.findPlatformUser("platform_admin")).thenReturn(user(1L, null, null));
        when(userAuthorityMapper.findActiveAuthorities(1L, null, "PLATFORM"))
                .thenReturn(List.of(authority(1L, null, "PLATFORM_ADMIN", "PLATFORM", null)));

        LoginIdentity identity = service.authenticate(new LoginCredentials("platform_admin", PASSWORD, null));

        assertThat(identity.permissionCodes()).isEmpty();
    }

    @Test
    void loginIdentityDoesNotExposePasswordHash() {
        when(sysUserMapper.findPlatformUser("platform_admin")).thenReturn(user(1L, null, null));
        when(userAuthorityMapper.findActiveAuthorities(1L, null, "PLATFORM"))
                .thenReturn(List.of(authority(1L, null, "PLATFORM_ADMIN", "PLATFORM", "tenant:create")));

        LoginIdentity identity = service.authenticate(new LoginCredentials("platform_admin", PASSWORD, null));

        assertThat(identity.toString()).doesNotContain(userPasswordHash).doesNotContain(PASSWORD);
        assertThat(identity.getClass().getDeclaredFields()).extracting("name").doesNotContain("passwordHash");
    }

    @Test
    void sensitiveObjectsDoNotExposePasswordsOrHashesInToString() {
        LoginCredentials credentials = new LoginCredentials("user-a", PASSWORD, "TENANT_A");
        SysUserDO user = new SysUserDO(1L, 1L, "user-a", "User A", userPasswordHash, "ACTIVE", "ACTIVE");

        assertThat(credentials.toString()).doesNotContain(PASSWORD);
        assertThat(user.toString()).doesNotContain(PASSWORD).doesNotContain(userPasswordHash);
        assertThat(new LoginIdentityAuthenticationException().getMessage())
                .doesNotContain("user-a", PASSWORD, userPasswordHash, "TENANT_A");
    }

    @Test
    void loginIdentityPermissionCodesAreImmutableAndDefensivelyCopied() {
        java.util.LinkedHashSet<String> mutable = new java.util.LinkedHashSet<>();
        mutable.add("order:create");
        LoginIdentity identity = new LoginIdentity(1L, 1L, "user-a", "User A",
                LoginIdentity.Scope.TENANT, mutable);

        mutable.add("admin:all");

        assertThat(identity.permissionCodes()).containsExactly("order:create");
        assertThatThrownBy(() -> identity.permissionCodes().add("admin:all"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void everyAccountFailurePathPerformsOnePasswordMatch() {
        PasswordEncoder encoder = Mockito.mock(PasswordEncoder.class);
        when(encoder.matches(anyString(), anyString())).thenReturn(true);
        LoginIdentityService isolated = new LoginIdentityService(
                sysUserMapper, userAuthorityMapper, encoder, dummyPasswordHash);

        when(sysUserMapper.findPlatformUser("missing")).thenReturn(null);
        when(sysUserMapper.findPlatformUser("disabled")).thenReturn(user(1L, null, null, "DISABLED"));
        when(sysUserMapper.findTenantUser("DISABLED_TENANT", "merchant"))
                .thenReturn(user(2L, 1L, "DISABLED"));
        when(sysUserMapper.findPlatformUser("mock_logistics_callback")).thenReturn(user(10L, null, null));
        when(userAuthorityMapper.findActiveAuthorities(10L, null, "PLATFORM"))
                .thenReturn(List.of(authority(10L, null, "MOCK_LOGISTICS_SYSTEM", "PLATFORM", "tracking:callback")));
        when(sysUserMapper.findPlatformUser("no-role")).thenReturn(user(3L, null, null));
        when(userAuthorityMapper.findActiveAuthorities(3L, null, "PLATFORM")).thenReturn(List.of());

        assertFailure(isolated, new LoginCredentials("missing", PASSWORD, null));
        assertFailure(isolated, new LoginCredentials("disabled", PASSWORD, null));
        assertFailure(isolated, new LoginCredentials("merchant", PASSWORD, "DISABLED_TENANT"));
        assertFailure(isolated, new LoginCredentials("mock_logistics_callback", PASSWORD, null));
        assertFailure(isolated, new LoginCredentials("no-role", PASSWORD, null));

        verify(encoder, times(5)).matches(anyString(), anyString());
    }

    @Test
    void mapperExceptionRemainsAnInfrastructureFailure() {
        RuntimeException infrastructureFailure = new RuntimeException("SQL mapping failure");
        when(sysUserMapper.findPlatformUser("platform_admin")).thenThrow(infrastructureFailure);

        assertThatThrownBy(() -> service.authenticate(new LoginCredentials("platform_admin", PASSWORD, null)))
                .isSameAs(infrastructureFailure);
    }

    @Test
    void authorityBindingExceptionIsNotConvertedToAuthenticationFailure() {
        SysUserDO user = user(2L, 1L, "ACTIVE");
        org.apache.ibatis.binding.BindingException bindingFailure =
                new org.apache.ibatis.binding.BindingException("Invalid bound statement");
        when(sysUserMapper.findTenantUser("TENANT_DEMO_001", "merchant_admin_001")).thenReturn(user);
        when(userAuthorityMapper.findActiveAuthorities(2L, 1L, "TENANT")).thenThrow(bindingFailure);

        assertThatThrownBy(() -> service.lookup(
                new LoginCredentials("merchant_admin_001", PASSWORD, "TENANT_DEMO_001")))
                .isSameAs(bindingFailure);
    }

    @Test
    void allAuthenticationFailuresUseAuth1001() {
        when(sysUserMapper.findPlatformUser("missing")).thenReturn(null);

        assertThatThrownBy(() -> service.authenticate(new LoginCredentials("missing", PASSWORD, null)))
                .isInstanceOf(LoginIdentityAuthenticationException.class)
                .satisfies(exception -> assertThat(((LoginIdentityAuthenticationException) exception).getErrorCode())
                        .isEqualTo(LoginIdentityAuthenticationException.ERROR_CODE));
    }

    private void assertFailure(LoginCredentials credentials) {
        assertFailure(service, credentials);
    }

    private void assertFailure(LoginIdentityService target, LoginCredentials credentials) {
        assertThatThrownBy(() -> target.authenticate(credentials))
                .isInstanceOf(LoginIdentityAuthenticationException.class)
                .hasMessage("Authentication failed");
    }

    private SysUserDO user(Long id, Long tenantId, String tenantStatus) {
        return user(id, tenantId, tenantStatus, "ACTIVE");
    }

    private SysUserDO user(Long id, Long tenantId, String tenantStatus, String status) {
        return new SysUserDO(id, tenantId, tenantId == null ? "platform_admin" : "merchant",
                "Test User", userPasswordHash, status, tenantStatus);
    }

    private UserAuthorityView authority(Long userId, Long tenantId, String roleCode, String roleScope,
                                        String permissionCode) {
        return authority(userId, tenantId, roleCode, roleScope, permissionCode, "ACTIVE");
    }

    private UserAuthorityView authority(Long userId, Long tenantId, String roleCode, String roleScope,
                                        String permissionCode, String roleStatus) {
        return new UserAuthorityView(userId, tenantId, roleCode, roleScope, roleStatus, permissionCode);
    }
}
