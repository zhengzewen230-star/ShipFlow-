package com.shipflow.auth;

import com.shipflow.auth.service.LoginIdentityAuthenticationException;
import com.shipflow.auth.service.PasswordAuthenticationService;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PasswordAuthenticationServiceTest {

    private static final String DUMMY_HASH = "$2a$10$7EqJtq98hPqEX7fNZaFWoO6uJ5Yp5hL6vZ2QzQ5G8m3v9n5X9S7Qe";

    @Test
    void bcryptPasswordMatches() {
        BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();
        String hash = encoder.encode("local-test-password");
        PasswordAuthenticationService service = new PasswordAuthenticationService(encoder, DUMMY_HASH);

        assertThatCode(() -> service.authenticate("local-test-password", hash)).doesNotThrowAnyException();
    }

    @Test
    void wrongPasswordUsesUnifiedAuthenticationFailure() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches("wrong", "stored-hash")).thenReturn(false);
        PasswordAuthenticationService service = new PasswordAuthenticationService(encoder, DUMMY_HASH);

        assertThatThrownBy(() -> service.authenticate("wrong", "stored-hash"))
                .isInstanceOf(LoginIdentityAuthenticationException.class)
                .hasMessage("Authentication failed");
    }

    @Test
    void blankPasswordFailsSafelyAndStillUsesEncoder() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches(anyString(), anyString())).thenReturn(false);
        PasswordAuthenticationService service = new PasswordAuthenticationService(encoder, DUMMY_HASH);

        assertThatThrownBy(() -> service.authenticate("   ", "stored-hash"))
                .isInstanceOf(LoginIdentityAuthenticationException.class);
        verify(encoder).matches("\u0000", "stored-hash");
    }

    @Test
    void missingStoredHashUsesDummyHash() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        when(encoder.matches(anyString(), anyString())).thenReturn(false);
        PasswordAuthenticationService service = new PasswordAuthenticationService(encoder, DUMMY_HASH);

        assertThatThrownBy(() -> service.authenticate("password", null))
                .isInstanceOf(LoginIdentityAuthenticationException.class);
        verify(encoder).matches("password", DUMMY_HASH);
    }

    @Test
    void encoderFailureRemainsAnInfrastructureFailure() {
        PasswordEncoder encoder = mock(PasswordEncoder.class);
        IllegalArgumentException infrastructureFailure = new IllegalArgumentException("Invalid stored hash");
        when(encoder.matches("password", "stored-hash")).thenThrow(infrastructureFailure);
        PasswordAuthenticationService service = new PasswordAuthenticationService(encoder, DUMMY_HASH);

        assertThatThrownBy(() -> service.authenticate("password", "stored-hash"))
                .isSameAs(infrastructureFailure);
        assertThat(service.toString()).doesNotContain("stored-hash");
    }
}
