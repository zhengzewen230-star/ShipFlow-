package com.shipflow.security.refresh;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class RefreshTokenSessionServiceTest {

    private static final Instant NOW = Instant.parse("2026-08-03T00:00:00Z");
    private RefreshSessionRepository repository;
    private RefreshTokenSessionService service;
    private RefreshTokenProperties properties;

    @BeforeEach
    void setUp() {
        repository = mock(RefreshSessionRepository.class);
        properties = new RefreshTokenProperties();
        properties.setHmacKey(java.util.Base64.getEncoder().encodeToString(
                "local-test-only-refresh-key-32-bytes".getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        RefreshTokenGenerator generator = new RefreshTokenGenerator(new java.security.SecureRandom());
        RefreshTokenHmacService hmac = new RefreshTokenHmacService(properties);
        service = new RefreshTokenSessionService(repository, generator, hmac, properties,
                Clock.fixed(NOW, ZoneOffset.UTC));
    }

    @Test
    void initialSessionCreatesActiveFamilyAndKeepsPlatformTenantNull() {
        whenInsertAssignsId(10L);

        RefreshTokenSessionResult result = service.issueInitial(7L, null);

        ArgumentCaptor<RefreshSessionDO> captor = ArgumentCaptor.forClass(RefreshSessionDO.class);
        verify(repository).insert(captor.capture());
        RefreshSessionDO saved = captor.getValue();
        assertThat(saved.userId()).isEqualTo(7L);
        assertThat(saved.tenantId()).isNull();
        assertThat(saved.status()).isEqualTo("ACTIVE");
        assertThat(saved.familyId()).isEqualTo(result.familyId());
        assertThat(saved.tokenHash()).matches("[0-9a-f]{64}");
        assertThat(result.sessionId()).isEqualTo(10L);
        assertThat(result.expiresAt()).isEqualTo(NOW.plusSeconds(30 * 24 * 60 * 60));
    }

    @Test
    void rotationPreservesFamilyPreviousSessionTenantAndAbsoluteExpiry() {
        RefreshToken oldToken = new RefreshToken("old-token");
        RefreshTokenHmacService hmac = new RefreshTokenHmacService(properties);
        LocalDateTime expiresAt = LocalDateTime.ofInstant(NOW.plusSeconds(29 * 24 * 60 * 60), ZoneOffset.UTC);
        RefreshSessionDO current = activeSession(10L, 20L, hmac.digest(oldToken).value(), 30L, expiresAt);
        when(repository.findByTokenHashForUpdate(current.tokenHash())).thenReturn(current);
        when(repository.markActiveAsRotated(30L, localNow())).thenReturn(1);
        whenInsertAssignsId(31L);

        RefreshTokenSessionResult result = service.rotate(oldToken);

        ArgumentCaptor<RefreshSessionDO> captor = ArgumentCaptor.forClass(RefreshSessionDO.class);
        verify(repository).insert(captor.capture());
        RefreshSessionDO successor = captor.getValue();
        assertThat(successor.familyId()).isEqualTo("family-30");
        assertThat(successor.previousSessionId()).isEqualTo(30L);
        assertThat(successor.userId()).isEqualTo(10L);
        assertThat(successor.tenantId()).isEqualTo(20L);
        assertThat(successor.expiresAt()).isEqualTo(expiresAt);
        assertThat(result.expiresAt()).isEqualTo(expiresAt.toInstant(ZoneOffset.UTC));
        assertThat(result.refreshToken()).isNotEqualTo(oldToken);
    }

    @Test
    void missingExpiredRevokedAndNullTokensUseSameFailure() {
        assertThatThrownBy(() -> service.rotate(null))
                .isInstanceOf(RefreshTokenAuthenticationException.class);

        RefreshToken missing = new RefreshToken("missing-token");
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(null);
        assertThatThrownBy(() -> service.rotate(missing))
                .isInstanceOf(RefreshTokenAuthenticationException.class)
                .hasMessage("Refresh authentication failed");

        RefreshSessionDO expired = activeSession(1L, 2L, hmac(missing), 1L,
                LocalDateTime.ofInstant(NOW.minusSeconds(1), ZoneOffset.UTC));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(expired);
        assertThatThrownBy(() -> service.rotate(missing)).isInstanceOf(RefreshTokenAuthenticationException.class);
        verify(repository).markExpired(1L, localNow());

        RefreshSessionDO revoked = activeSession(1L, 2L, hmac(missing), 2L,
                LocalDateTime.ofInstant(NOW.plusSeconds(10), ZoneOffset.UTC));
        revoked = new RefreshSessionDO(revoked.id(), revoked.userId(), revoked.tenantId(), revoked.tokenHash(),
                revoked.familyId(), revoked.previousSessionId(), "REVOKED", revoked.expiresAt(), null,
                revoked.createdAt(), revoked.updatedAt());
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(revoked);
        assertThatThrownBy(() -> service.rotate(missing)).isInstanceOf(RefreshTokenAuthenticationException.class);
    }

    @Test
    void rotatedReplayRevokesEntireFamily() {
        RefreshToken token = new RefreshToken("rotated-token");
        RefreshSessionDO rotated = new RefreshSessionDO(40L, 1L, 2L, hmac(token), "family-replay", null,
                "ROTATED", LocalDateTime.ofInstant(NOW.plusSeconds(100), ZoneOffset.UTC), null,
                localNow(), localNow());
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(rotated);

        assertThatThrownBy(() -> service.rotate(token)).isInstanceOf(RefreshTokenAuthenticationException.class);

        verify(repository).revokeFamily("family-replay", localNow());
        verify(repository, never()).insert(any(RefreshSessionDO.class));
    }

    @Test
    void failedAtomicStateUpdateCannotCreateSecondSuccessorAndRevokesFamily() {
        RefreshToken token = new RefreshToken("concurrent-token");
        RefreshSessionDO current = activeSession(1L, 2L, hmac(token), 50L,
                LocalDateTime.ofInstant(NOW.plusSeconds(100), ZoneOffset.UTC));
        when(repository.findByTokenHashForUpdate(anyString())).thenReturn(current);
        when(repository.markActiveAsRotated(50L, localNow())).thenReturn(0);

        assertThatThrownBy(() -> service.rotate(token)).isInstanceOf(RefreshTokenAuthenticationException.class);

        verify(repository).revokeFamily("family-50", localNow());
        verify(repository, never()).insert(any(RefreshSessionDO.class));
    }

    private void whenInsertAssignsId(Long id) {
        org.mockito.Mockito.doAnswer(invocation -> {
            invocation.<RefreshSessionDO>getArgument(0).setId(id);
            return null;
        }).when(repository).insert(any(RefreshSessionDO.class));
    }

    private RefreshSessionDO activeSession(Long userId, Long tenantId, String hash, Long id,
                                           LocalDateTime expiresAt) {
        return new RefreshSessionDO(id, userId, tenantId, hash, "family-" + id, null, "ACTIVE",
                expiresAt, null, localNow(), localNow());
    }

    private String hmac(RefreshToken token) {
        return new RefreshTokenHmacService(properties).digest(token).value();
    }

    private LocalDateTime localNow() {
        return LocalDateTime.ofInstant(NOW, ZoneOffset.UTC);
    }
}
