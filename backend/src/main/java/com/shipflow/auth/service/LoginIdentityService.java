package com.shipflow.auth.service;

import com.shipflow.auth.mapper.SysUserMapper;
import com.shipflow.auth.mapper.UserAuthorityMapper;
import com.shipflow.auth.model.LoginCredentials;
import com.shipflow.auth.model.LoginIdentity;
import com.shipflow.auth.model.LoginIdentityLookup;
import com.shipflow.auth.model.SysUserDO;
import com.shipflow.auth.model.UserAuthorityView;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Performs exact-scope account lookup, BCrypt verification and one-shot authority loading. */
public class LoginIdentityService {

    private static final String ACTIVE = "ACTIVE";
    private static final String PLATFORM = "PLATFORM";
    private static final String TENANT = "TENANT";
    private static final String MOCK_SYSTEM_ROLE = "MOCK_LOGISTICS_SYSTEM";

    private final SysUserMapper sysUserMapper;
    private final UserAuthorityMapper userAuthorityMapper;
    private final PasswordEncoder passwordEncoder;
    private final String dummyPasswordHash;

    public LoginIdentityService(
            SysUserMapper sysUserMapper,
            UserAuthorityMapper userAuthorityMapper,
            PasswordEncoder passwordEncoder,
            String dummyPasswordHash) {
        this.sysUserMapper = sysUserMapper;
        this.userAuthorityMapper = userAuthorityMapper;
        this.passwordEncoder = passwordEncoder;
        if (dummyPasswordHash == null || dummyPasswordHash.isBlank()) {
            throw new IllegalArgumentException("Dummy password hash is required");
        }
        this.dummyPasswordHash = dummyPasswordHash;
    }

    public LoginIdentity authenticate(LoginCredentials credentials) {
        try {
            String password = credentials == null || credentials.password() == null
                    ? "\u0000"
                    : credentials.password();
            String username = credentials == null ? null : credentials.username();
            String tenantCode = credentials == null ? null : credentials.tenantCode();

            SysUserDO user = findUser(username, tenantCode);
            boolean passwordMatches = passwordEncoder.matches(
                    password, user == null ? dummyPasswordHash : user.passwordHash());
            if (user == null || !passwordMatches || !isValidUser(user)) {
                throw failure();
            }

            String roleScope = user.tenantId() == null ? PLATFORM : TENANT;
            List<UserAuthorityView> authorities = userAuthorityMapper.findActiveAuthorities(
                    user.id(), user.tenantId(), roleScope);
            return toIdentity(user, roleScope, authorities);
        } catch (LoginIdentityAuthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            // Do not expose mapper, encoder or driver messages to a public login endpoint.
            throw failure();
        }
    }

    /** Loads the identity and internal password hash in one database pass. */
    public LoginIdentityLookup lookup(LoginCredentials credentials) {
        try {
            String username = credentials == null ? null : credentials.username();
            String tenantCode = credentials == null ? null : credentials.tenantCode();
            SysUserDO user = findUser(username, tenantCode);
            if (user == null || !isValidUser(user)) {
                throw failure();
            }

            String roleScope = user.tenantId() == null ? PLATFORM : TENANT;
            List<UserAuthorityView> authorities = userAuthorityMapper.findActiveAuthorities(
                    user.id(), user.tenantId(), roleScope);
            return new LoginIdentityLookup(toIdentity(user, roleScope, authorities), user.passwordHash());
        } catch (LoginIdentityAuthenticationException exception) {
            throw exception;
        } catch (RuntimeException exception) {
            throw failure();
        }
    }

    private SysUserDO findUser(String username, String tenantCode) {
        if (isBlank(username)) {
            return null;
        }
        return isBlank(tenantCode)
                ? sysUserMapper.findPlatformUser(username)
                : sysUserMapper.findTenantUser(tenantCode, username);
    }

    private boolean isValidUser(SysUserDO user) {
        return ACTIVE.equals(user.status())
                && (user.tenantId() == null || ACTIVE.equals(user.tenantStatus()));
    }

    private LoginIdentity toIdentity(SysUserDO user, String roleScope, List<UserAuthorityView> authorities) {
        if (authorities == null || authorities.isEmpty()) {
            throw failure();
        }

        Set<String> permissionCodes = new LinkedHashSet<>();
        boolean validRole = false;
        for (UserAuthorityView authority : authorities) {
            if (authority == null
                    || !user.id().equals(authority.userId())
                    || !sameTenant(user.tenantId(), authority.tenantId())
                    || isBlank(authority.roleCode())
                    || !ACTIVE.equals(authority.roleStatus())
                    || !roleScope.equals(authority.roleScope())
                    || authority.roleCode().startsWith(MOCK_SYSTEM_ROLE)) {
                throw failure();
            }
            validRole = true;
            if (!isBlank(authority.permissionCode())) {
                permissionCodes.add(authority.permissionCode());
            }
        }
        if (!validRole) {
            throw failure();
        }

        return new LoginIdentity(
                user.id(),
                user.tenantId(),
                user.username(),
                user.displayName(),
                user.tenantId() == null ? LoginIdentity.Scope.PLATFORM : LoginIdentity.Scope.TENANT,
                permissionCodes);
    }

    private boolean sameTenant(Long expected, Long actual) {
        return expected == null ? actual == null : expected.equals(actual);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private LoginIdentityAuthenticationException failure() {
        return new LoginIdentityAuthenticationException();
    }
}
