package com.shipflow.security.refresh;

import java.time.Instant;

/** Raw token hand-off to the future application layer; never log this object. */
public final class RefreshTokenSessionResult {

    private final RefreshToken refreshToken;
    private final Long sessionId;
    private final String familyId;
    private final Long userId;
    private final Long tenantId;
    private final Instant expiresAt;

    public RefreshTokenSessionResult(RefreshToken refreshToken, Long sessionId, String familyId,
                                     Long userId, Long tenantId, Instant expiresAt) {
        this.refreshToken = refreshToken;
        this.sessionId = sessionId;
        this.familyId = familyId;
        this.userId = userId;
        this.tenantId = tenantId;
        this.expiresAt = expiresAt;
    }

    public RefreshToken refreshToken() { return refreshToken; }
    public Long sessionId() { return sessionId; }
    public String familyId() { return familyId; }
    public Long userId() { return userId; }
    public Long tenantId() { return tenantId; }
    public Instant expiresAt() { return expiresAt; }

    @Override
    public String toString() {
        return "RefreshTokenSessionResult{sessionId=" + sessionId + ", familyId='redacted'"
                + ", userId=" + userId + ", tenantId=" + tenantId + ", expiresAt=" + expiresAt + '}';
    }
}
