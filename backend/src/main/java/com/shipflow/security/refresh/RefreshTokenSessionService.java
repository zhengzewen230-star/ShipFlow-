package com.shipflow.security.refresh;

import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

/** Implements absolute-family expiry, row-locked rotation and family-wide replay revocation. */
public class RefreshTokenSessionService {

    private static final String ACTIVE = "ACTIVE";
    private static final String ROTATED = "ROTATED";
    private static final String EXPIRED = "EXPIRED";

    private final RefreshSessionRepository repository;
    private final RefreshTokenGenerator tokenGenerator;
    private final RefreshTokenHmacService hmacService;
    private final RefreshTokenProperties properties;
    private final Clock clock;

    public RefreshTokenSessionService(RefreshSessionRepository repository,
                                      RefreshTokenGenerator tokenGenerator,
                                      RefreshTokenHmacService hmacService,
                                      RefreshTokenProperties properties,
                                      Clock clock) {
        this.repository = repository;
        this.tokenGenerator = tokenGenerator;
        this.hmacService = hmacService;
        this.properties = properties;
        this.clock = clock;
        properties.validate();
    }

    @Transactional
    public RefreshTokenSessionResult issueInitial(Long userId, Long tenantId) {
        if (userId == null) {
            throw new IllegalArgumentException("Refresh session user is required");
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        Instant expiresAt = now.plus(properties.getFamilyTtl());
        return persist(userId, tenantId, UUID.randomUUID().toString(), null, expiresAt, now);
    }

    @Transactional(noRollbackFor = RefreshTokenAuthenticationException.class)
    public RefreshTokenSessionResult rotate(RefreshToken rawToken) {
        if (rawToken == null) {
            throw failure();
        }
        Instant now = clock.instant().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime databaseNow = toUtc(now);
        String tokenHash = hmacService.digest(rawToken).value();
        RefreshSessionDO current = repository.findByTokenHashForUpdate(tokenHash);
        if (current == null) {
            throw failure();
        }
        if (!now.isBefore(toInstant(current.expiresAt()))) {
            if (ACTIVE.equals(current.status())) {
                repository.markExpired(current.id(), databaseNow);
            }
            throw failure();
        }
        if (ROTATED.equals(current.status())) {
            repository.revokeFamily(current.familyId(), databaseNow);
            throw failure();
        }
        if (!ACTIVE.equals(current.status())) {
            throw failure();
        }
        if (repository.markActiveAsRotated(current.id(), databaseNow) != 1) {
            repository.revokeFamily(current.familyId(), databaseNow);
            throw failure();
        }
        return persist(current.userId(), current.tenantId(), current.familyId(), current.id(),
                toInstant(current.expiresAt()), now);
    }

    @Transactional
    public void revokeFamily(String familyId) {
        if (familyId == null || familyId.isBlank()) {
            return;
        }
        repository.revokeFamily(familyId, toUtc(clock.instant()));
    }

    public java.util.List<RefreshSessionDO> findFamily(String familyId) {
        return repository.findFamily(familyId);
    }

    private RefreshTokenSessionResult persist(Long userId, Long tenantId, String familyId,
                                              Long previousSessionId, Instant expiresAt, Instant now) {
        RefreshToken token = tokenGenerator.generate();
        RefreshSessionDO session = new RefreshSessionDO(
                null, userId, tenantId, hmacService.digest(token).value(), familyId,
                previousSessionId, ACTIVE, toUtc(expiresAt), null, toUtc(now), toUtc(now));
        repository.insert(session);
        return new RefreshTokenSessionResult(token, session.id(), familyId, userId, tenantId, expiresAt);
    }

    private Instant toInstant(LocalDateTime value) {
        return value.toInstant(ZoneOffset.UTC);
    }

    private LocalDateTime toUtc(Instant value) {
        return LocalDateTime.ofInstant(value, ZoneOffset.UTC);
    }

    private RefreshTokenAuthenticationException failure() {
        return new RefreshTokenAuthenticationException();
    }
}
