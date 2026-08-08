package com.shipflow.security.refresh;

import java.time.LocalDateTime;

/** Persistence projection for auth_refresh_session; token_hash is never rendered. */
public final class RefreshSessionDO {

    private Long id;
    private final Long userId;
    private final Long tenantId;
    private final String tokenHash;
    private final String familyId;
    private final Long previousSessionId;
    private final String status;
    private final LocalDateTime expiresAt;
    private final LocalDateTime revokedAt;
    private final LocalDateTime createdAt;
    private final LocalDateTime updatedAt;

    public RefreshSessionDO(Long id, Long userId, Long tenantId, String tokenHash, String familyId,
                            Long previousSessionId, String status, LocalDateTime expiresAt,
                            LocalDateTime revokedAt, LocalDateTime createdAt, LocalDateTime updatedAt) {
        this.id = id;
        this.userId = userId;
        this.tenantId = tenantId;
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.previousSessionId = previousSessionId;
        this.status = status;
        this.expiresAt = expiresAt;
        this.revokedAt = revokedAt;
        this.createdAt = createdAt;
        this.updatedAt = updatedAt;
    }

    public Long id() { return id; }
    public void setId(Long id) { this.id = id; }
    public Long userId() { return userId; }
    public Long tenantId() { return tenantId; }
    public String tokenHash() { return tokenHash; }
    public String familyId() { return familyId; }
    public Long previousSessionId() { return previousSessionId; }
    public String status() { return status; }
    public LocalDateTime expiresAt() { return expiresAt; }
    public LocalDateTime revokedAt() { return revokedAt; }
    public LocalDateTime createdAt() { return createdAt; }
    public LocalDateTime updatedAt() { return updatedAt; }

    public Long getId() { return id; }
    public Long getUserId() { return userId; }
    public Long getTenantId() { return tenantId; }
    public String getTokenHash() { return tokenHash; }
    public String getFamilyId() { return familyId; }
    public Long getPreviousSessionId() { return previousSessionId; }
    public String getStatus() { return status; }
    public LocalDateTime getExpiresAt() { return expiresAt; }
    public LocalDateTime getRevokedAt() { return revokedAt; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }

    @Override
    public String toString() {
        return "RefreshSessionDO{id=" + id + ", userId=" + userId + ", tenantId=" + tenantId
                + ", familyId='redacted', previousSessionId=" + previousSessionId
                + ", status='" + status + "', expiresAt=" + expiresAt + ", revokedAt=" + revokedAt
                + ", createdAt=" + createdAt + ", updatedAt=" + updatedAt + '}';
    }
}
