package com.shipflow.auth;

import com.shipflow.auth.application.LoginApplicationService;
import com.shipflow.auth.application.model.LoginCommand;
import com.shipflow.auth.application.model.LoginResult;
import com.shipflow.auth.mapper.SysUserMapper;
import com.shipflow.auth.mapper.UserAuthorityMapper;
import com.shipflow.auth.model.LoginCredentials;
import com.shipflow.auth.model.LoginIdentity;
import com.shipflow.auth.model.LoginIdentityLookup;
import com.shipflow.auth.service.LoginIdentityAuthenticationException;
import com.shipflow.auth.service.LoginIdentityService;
import com.shipflow.auth.service.PasswordAuthenticationService;
import com.shipflow.security.jwt.AccessTokenPrincipal;
import com.shipflow.security.jwt.AccessTokenService;
import com.shipflow.security.jwt.JwtProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class LoginApplicationServiceTest {

    private LoginIdentityService identityService;
    private PasswordAuthenticationService passwordService;
    private AccessTokenService tokenService;
    private JwtProperties properties;
    private LoginApplicationService service;

    @BeforeEach
    void setUp() {
        identityService = mock(LoginIdentityService.class);
        passwordService = mock(PasswordAuthenticationService.class);
        tokenService = mock(AccessTokenService.class);
        properties = new JwtProperties();
        properties.setIssuer("shipflow-test");
        properties.setAudience("shipflow-api");
        properties.setActiveKid("test-key");
        properties.setAccessTokenTtl(Duration.ofMinutes(15));
        service = new LoginApplicationService(identityService, passwordService, tokenService, properties);
    }

    @Test
    void tenantLoginBuildsTenantPrincipalAndResult() {
        LoginIdentity identity = tenantIdentity();
        when(identityService.lookup(org.mockito.ArgumentMatchers.any(LoginCredentials.class)))
                .thenReturn(new LoginIdentityLookup(identity, "hash"));
        when(tokenService.issue(org.mockito.ArgumentMatchers.any())).thenReturn("access-token");

        LoginResult result = service.login(new LoginCommand("merchant", "password", "TENANT_A"));

        ArgumentCaptor<AccessTokenPrincipal> captor = ArgumentCaptor.forClass(AccessTokenPrincipal.class);
        verify(tokenService).issue(captor.capture());
        assertThat(captor.getValue()).isEqualTo(new AccessTokenPrincipal("100", AccessTokenPrincipal.Scope.TENANT, "20"));
        assertThat(result.accessToken()).isEqualTo("access-token");
        assertThat(result.tokenType()).isEqualTo("Bearer");
        assertThat(result.expiresIn()).isEqualTo(900);
        assertThat(result.identity()).isEqualTo(identity);
    }

    @Test
    void platformLoginOmitsTenantFromPrincipal() {
        LoginIdentity identity = new LoginIdentity(101L, null, "platform", "Platform", LoginIdentity.Scope.PLATFORM,
                Set.of("tenant:create"));
        when(identityService.lookup(org.mockito.ArgumentMatchers.any(LoginCredentials.class)))
                .thenReturn(new LoginIdentityLookup(identity, "hash"));
        when(tokenService.issue(org.mockito.ArgumentMatchers.any())).thenReturn("platform-token");

        service.login(new LoginCommand("platform", "password", null));

        ArgumentCaptor<AccessTokenPrincipal> captor = ArgumentCaptor.forClass(AccessTokenPrincipal.class);
        verify(tokenService).issue(captor.capture());
        assertThat(captor.getValue().scope()).isEqualTo(AccessTokenPrincipal.Scope.PLATFORM);
        assertThat(captor.getValue().tenantId()).isNull();
    }

    @Test
    void passwordFailureStopsTokenIssuanceAndUsesAuth1001() {
        LoginIdentity identity = tenantIdentity();
        when(identityService.lookup(org.mockito.ArgumentMatchers.any(LoginCredentials.class)))
                .thenReturn(new LoginIdentityLookup(identity, "hash"));
        doNothing().when(passwordService).authenticate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        org.mockito.Mockito.doThrow(new LoginIdentityAuthenticationException())
                .when(passwordService).authenticate("wrong", "hash");

        assertThatThrownBy(() -> service.login(new LoginCommand("merchant", "wrong", "TENANT_A")))
                .isInstanceOf(LoginIdentityAuthenticationException.class)
                .hasMessage("Authentication failed");
        verify(tokenService, never()).issue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void identityFailureStopsPasswordAndTokenProcessing() {
        when(identityService.lookup(org.mockito.ArgumentMatchers.any(LoginCredentials.class)))
                .thenThrow(new LoginIdentityAuthenticationException());

        assertThatThrownBy(() -> service.login(new LoginCommand("missing", "password", "TENANT_A")))
                .isInstanceOf(LoginIdentityAuthenticationException.class);
        verify(passwordService, never()).authenticate(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.anyString());
        verify(tokenService, never()).issue(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void tokenServiceFailureIsNotMappedToAuthenticationFailure() {
        when(identityService.lookup(org.mockito.ArgumentMatchers.any(LoginCredentials.class)))
                .thenReturn(new LoginIdentityLookup(tenantIdentity(), "hash"));
        when(tokenService.issue(org.mockito.ArgumentMatchers.any()))
                .thenThrow(new IllegalStateException("JWT configuration failure"));

        assertThatThrownBy(() -> service.login(new LoginCommand("merchant", "password", "TENANT_A")))
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("JWT configuration failure");
    }

    @Test
    void invalidCommandDoesNotQueryIdentity() {
        assertThatThrownBy(() -> service.login(new LoginCommand(" ", "password", "TENANT_A")))
                .isInstanceOf(LoginIdentityAuthenticationException.class);
        assertThatThrownBy(() -> service.login(new LoginCommand("merchant", "", "TENANT_A")))
                .isInstanceOf(LoginIdentityAuthenticationException.class);
        verify(identityService, never()).lookup(org.mockito.ArgumentMatchers.any(LoginCredentials.class));
    }

    @Test
    void credentialsArePassedToIdentityServiceWithoutFallback() {
        when(identityService.lookup(org.mockito.ArgumentMatchers.any(LoginCredentials.class)))
                .thenReturn(new LoginIdentityLookup(tenantIdentity(), "hash"));
        when(tokenService.issue(org.mockito.ArgumentMatchers.any())).thenReturn("token");

        service.login(new LoginCommand("merchant", "password", "TENANT_A"));

        ArgumentCaptor<LoginCredentials> captor = ArgumentCaptor.forClass(LoginCredentials.class);
        verify(identityService).lookup(captor.capture());
        assertThat(captor.getValue().username()).isEqualTo("merchant");
        assertThat(captor.getValue().tenantCode()).isEqualTo("TENANT_A");
        assertThat(captor.getValue().password()).isEqualTo("password");
    }

    @Test
    void resultDoesNotExposePasswordHashOrRefreshToken() {
        LoginIdentity identity = tenantIdentity();
        when(identityService.lookup(org.mockito.ArgumentMatchers.any(LoginCredentials.class)))
                .thenReturn(new LoginIdentityLookup(identity, "stored-hash"));
        when(tokenService.issue(org.mockito.ArgumentMatchers.any())).thenReturn("access-token");

        LoginResult result = service.login(new LoginCommand("merchant", "password", "TENANT_A"));

        assertThat(result.toString()).doesNotContain("stored-hash", "password", "refreshToken");
        assertThat(result.getClass().getDeclaredFields()).extracting("name")
                .doesNotContain("password", "passwordHash", "refreshToken");
    }

    @Test
    void sensitiveInternalObjectsHaveSafeToStringValues() {
        String password = "local-only-password";
        String hash = "$2a$10$local-only-test-hash-value";
        String accessToken = "local-only-access-token";

        LoginCommand command = new LoginCommand("merchant", password, "TENANT_A");
        LoginCredentials credentials = new LoginCredentials("merchant", password, "TENANT_A");
        LoginIdentity identity = tenantIdentity();
        LoginIdentityLookup lookup = new LoginIdentityLookup(identity, hash);
        LoginResult result = new LoginResult(accessToken, "Bearer", 900, identity);

        assertThat(command.toString()).doesNotContain(password, hash, accessToken);
        assertThat(credentials.toString()).doesNotContain(password, hash, accessToken);
        assertThat(lookup.toString()).doesNotContain(hash, password, accessToken);
        assertThat(result.toString()).doesNotContain(accessToken, hash, password);
        assertThat(new LoginIdentityAuthenticationException().getMessage())
                .doesNotContain(password, hash, accessToken);
    }

    @Test
    void accessTokenPrincipalDefaultToStringContainsNoSecretMaterial() {
        AccessTokenPrincipal principal = new AccessTokenPrincipal(
                "100", AccessTokenPrincipal.Scope.TENANT, "20");

        assertThat(principal.toString()).doesNotContain("password", "hash", "private key", "access-token");
    }

    private LoginIdentity tenantIdentity() {
        return new LoginIdentity(100L, 20L, "merchant", "Merchant", LoginIdentity.Scope.TENANT,
                Set.of("order:create"));
    }
}
