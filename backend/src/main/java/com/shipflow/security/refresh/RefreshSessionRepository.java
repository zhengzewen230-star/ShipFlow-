package com.shipflow.security.refresh;

import java.time.LocalDateTime;
import java.util.List;

public interface RefreshSessionRepository {

    void insert(RefreshSessionDO session);

    RefreshSessionDO findByTokenHashForUpdate(String tokenHash);

    int markActiveAsRotated(Long id, LocalDateTime updatedAt);

    int markExpired(Long id, LocalDateTime updatedAt);

    int revokeFamily(String familyId, LocalDateTime revokedAt);

    List<RefreshSessionDO> findFamily(String familyId);
}
