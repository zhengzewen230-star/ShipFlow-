package com.shipflow.security.authorization;

import com.shipflow.auth.model.LoginIdentity;
import com.shipflow.auth.model.LoginIdentityLookup;
import com.shipflow.auth.service.LoginIdentityAuthenticationException;
import com.shipflow.auth.service.LoginIdentityService;
import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class CurrentCallerServiceTest {

    @Test
    void loadsLiveIdentityAndMapsPermissionsToAuthorities() {
        LoginIdentityService identityService = mock(LoginIdentityService.class);
        LoginIdentity identity = new LoginIdentity(
                7L, 11L, "operator", "Operator", LoginIdentity.Scope.TENANT,
                Set.of("store:read", "user:read"));
        when(identityService.reloadByUserId(7L, 11L))
                .thenReturn(new LoginIdentityLookup(identity, "redacted-in-test"));

        CurrentCallerService service = new CurrentCallerService(identityService);
        CurrentCaller caller = service.load(7L, 11L);

        assertThat(caller.userId()).isEqualTo(7L);
        assertThat(caller.tenantId()).isEqualTo(11L);
        assertThat(caller.scope()).isEqualTo(LoginIdentity.Scope.TENANT);
        assertThat(service.authorities(caller))
                .extracting("authority")
                .containsExactlyInAnyOrder("scope:TENANT", "store:read", "user:read");
    }

    @Test
    void disabledOrMissingIdentityIsRejectedImmediately() {
        LoginIdentityService identityService = mock(LoginIdentityService.class);
        when(identityService.reloadByUserId(7L, 11L))
                .thenThrow(new LoginIdentityAuthenticationException());

        CurrentCallerService service = new CurrentCallerService(identityService);

        assertThatThrownBy(() -> service.load(7L, 11L))
                .isInstanceOf(org.springframework.security.authentication.BadCredentialsException.class);
    }

    @Test
    void infrastructureFailureIsNotConvertedToAuthenticationFailure() {
        LoginIdentityService identityService = mock(LoginIdentityService.class);
        IllegalStateException infrastructureFailure = new IllegalStateException("mapper failure");
        when(identityService.reloadByUserId(7L, 11L)).thenThrow(infrastructureFailure);

        CurrentCallerService service = new CurrentCallerService(identityService);

        assertThatThrownBy(() -> service.load(7L, 11L))
                .isSameAs(infrastructureFailure);
    }
}
