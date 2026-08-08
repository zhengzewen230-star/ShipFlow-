package com.shipflow.auth.application;

import com.shipflow.auth.application.model.LoginCommand;
import com.shipflow.auth.application.model.LoginResult;
import com.shipflow.auth.model.LoginIdentity;
import com.shipflow.auth.model.LoginIdentityLookup;
import com.shipflow.auth.model.LoginCredentials;
import com.shipflow.auth.service.LoginIdentityAuthenticationException;
import com.shipflow.auth.service.LoginIdentityService;
import com.shipflow.auth.service.PasswordAuthenticationService;
import com.shipflow.security.jwt.AccessTokenService;
import com.shipflow.security.jwt.AccessTokenPrincipal;
import com.shipflow.security.jwt.JwtProperties;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;

/** Orchestrates identity lookup, password verification and access-token issuance. */
@Service
@Profile("!test")
public final class LoginApplicationService {

    private final LoginIdentityService loginIdentityService;
    private final PasswordAuthenticationService passwordAuthenticationService;
    private final AccessTokenService accessTokenService;
    private final JwtProperties jwtProperties;

    public LoginApplicationService(
            LoginIdentityService loginIdentityService,
            PasswordAuthenticationService passwordAuthenticationService,
            AccessTokenService accessTokenService,
            JwtProperties jwtProperties) {
        this.loginIdentityService = loginIdentityService;
        this.passwordAuthenticationService = passwordAuthenticationService;
        this.accessTokenService = accessTokenService;
        this.jwtProperties = jwtProperties;
    }

    public LoginResult login(LoginCommand command) {
        validate(command);
        LoginCredentials credentials = new LoginCredentials(
                command.username(), command.password(), command.tenantCode());
        LoginIdentityLookup lookup = loginIdentityService.lookup(credentials);
        passwordAuthenticationService.authenticate(command.password(), lookup.passwordHash());

        LoginIdentity identity = lookup.identity();
        AccessTokenPrincipal principal = new AccessTokenPrincipal(
                identity.userId().toString(),
                identity.scope() == LoginIdentity.Scope.PLATFORM
                        ? AccessTokenPrincipal.Scope.PLATFORM
                        : AccessTokenPrincipal.Scope.TENANT,
                identity.tenantId() == null ? null : identity.tenantId().toString());
        String accessToken = accessTokenService.issue(principal);
        return new LoginResult(accessToken, "Bearer", jwtProperties.getAccessTokenTtl().getSeconds(), identity);
    }

    private void validate(LoginCommand command) {
        if (command == null || isBlank(command.username()) || isBlank(command.password())) {
            throw new LoginIdentityAuthenticationException();
        }
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
