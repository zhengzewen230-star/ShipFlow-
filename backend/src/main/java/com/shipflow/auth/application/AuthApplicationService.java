package com.shipflow.auth.application;

import com.shipflow.auth.application.model.LoginCommand;
import com.shipflow.auth.application.model.LoginResult;
import com.shipflow.auth.model.LoginIdentity;
import com.shipflow.auth.service.LoginIdentityService;
import com.shipflow.security.jwt.AccessTokenPrincipal;
import com.shipflow.security.jwt.AccessTokenService;
import com.shipflow.security.jwt.JwtProperties;
import com.shipflow.security.refresh.RefreshToken;
import com.shipflow.security.refresh.RefreshTokenSessionResult;
import com.shipflow.security.refresh.RefreshTokenSessionService;
import org.springframework.security.oauth2.jwt.Jwt;

public class AuthApplicationService {
    private final LoginApplicationService loginApplicationService;
    private final LoginIdentityService identityService;
    private final AccessTokenService accessTokenService;
    private final RefreshTokenSessionService refreshSessions;
    private final JwtProperties jwtProperties;

    public AuthApplicationService(LoginApplicationService loginApplicationService,
                                  LoginIdentityService identityService,
                                  AccessTokenService accessTokenService,
                                  RefreshTokenSessionService refreshSessions,
                                  JwtProperties jwtProperties) {
        this.loginApplicationService = loginApplicationService;
        this.identityService = identityService;
        this.accessTokenService = accessTokenService;
        this.refreshSessions = refreshSessions;
        this.jwtProperties = jwtProperties;
    }

    public AuthSessionResult login(LoginCommand command) {
        LoginResult access = loginApplicationService.login(command);
        RefreshTokenSessionResult refresh = refreshSessions.issueInitial(
                access.identity().userId(), access.identity().tenantId());
        return new AuthSessionResult(access, refresh.refreshToken());
    }

    public AuthSessionResult refresh(String rawToken) {
        RefreshTokenSessionResult rotated = refreshSessions.rotate(
                rawToken == null ? null : new RefreshToken(rawToken));
        var lookup = identityService.reloadByUserId(rotated.userId(), rotated.tenantId());
        LoginIdentity identity = lookup.identity();
        LoginResult access = issueAccess(identity);
        return new AuthSessionResult(access, rotated.refreshToken());
    }

    public void logout(String rawToken) {
        refreshSessions.revokeByToken(rawToken == null ? null : new RefreshToken(rawToken));
    }

    public LoginIdentity currentUser(Jwt jwt) {
        if (jwt == null || jwt.getSubject() == null) {
            throw new com.shipflow.security.refresh.RefreshTokenAuthenticationException();
        }
        Long tenantId = jwt.getClaimAsString("tenant_id") == null
                ? null : Long.valueOf(jwt.getClaimAsString("tenant_id"));
        return identityService.reloadByUserId(Long.valueOf(jwt.getSubject()), tenantId).identity();
    }

    private LoginResult issueAccess(LoginIdentity identity) {
        var principal = new AccessTokenPrincipal(identity.userId().toString(),
                identity.scope() == LoginIdentity.Scope.PLATFORM
                        ? AccessTokenPrincipal.Scope.PLATFORM : AccessTokenPrincipal.Scope.TENANT,
                identity.tenantId() == null ? null : identity.tenantId().toString());
        return new LoginResult(accessTokenService.issue(principal), "Bearer",
                jwtProperties.getAccessTokenTtl().getSeconds(), identity);
    }
}
