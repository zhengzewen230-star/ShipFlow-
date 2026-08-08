package com.shipflow.security.authorization;

import com.shipflow.auth.model.LoginIdentityLookup;
import com.shipflow.auth.service.LoginIdentityService;
import com.shipflow.auth.service.LoginIdentityAuthenticationException;
import org.springframework.context.annotation.Profile;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;

import java.util.Collection;

/** Rebuilds the caller and authorities from the live authentication identity. */
@Service
@Profile("!test")
public class CurrentCallerService {
    private final LoginIdentityService identityService;

    public CurrentCallerService(LoginIdentityService identityService) {
        this.identityService = identityService;
    }

    public CurrentCaller load(Long userId, Long tenantId) {
        try {
            LoginIdentityLookup lookup = identityService.reloadByUserId(userId, tenantId);
            var identity = lookup.identity();
            return new CurrentCaller(
                    identity.userId(),
                    identity.tenantId(),
                    identity.scope(),
                    identity.permissionCodes());
        } catch (LoginIdentityAuthenticationException exception) {
            throw new BadCredentialsException("Current caller is not active", exception);
        }
    }

    public Collection<GrantedAuthority> authorities(CurrentCaller caller) {
        java.util.ArrayList<GrantedAuthority> authorities = new java.util.ArrayList<>();
        authorities.add(new SimpleGrantedAuthority("scope:" + caller.scope().name()));
        authorities.addAll(caller.permissionCodes().stream()
                .<GrantedAuthority>map(SimpleGrantedAuthority::new)
                .toList());
        return authorities;
    }
}
