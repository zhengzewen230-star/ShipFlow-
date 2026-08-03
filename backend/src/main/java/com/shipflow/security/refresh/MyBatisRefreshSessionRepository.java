package com.shipflow.security.refresh;

import java.time.LocalDateTime;
import java.util.List;

public class MyBatisRefreshSessionRepository implements RefreshSessionRepository {

    private final RefreshSessionMapper mapper;

    public MyBatisRefreshSessionRepository(RefreshSessionMapper mapper) {
        this.mapper = mapper;
    }

    @Override
    public void insert(RefreshSessionDO session) {
        mapper.insert(session);
    }

    @Override
    public RefreshSessionDO findByTokenHashForUpdate(String tokenHash) {
        return mapper.findByTokenHashForUpdate(tokenHash);
    }

    @Override
    public int markActiveAsRotated(Long id, LocalDateTime updatedAt) {
        return mapper.markActiveAsRotated(id, updatedAt);
    }

    @Override
    public int markExpired(Long id, LocalDateTime updatedAt) {
        return mapper.markExpired(id, updatedAt);
    }

    @Override
    public int revokeFamily(String familyId, LocalDateTime revokedAt) {
        return mapper.revokeFamily(familyId, revokedAt);
    }

    @Override
    public List<RefreshSessionDO> findFamily(String familyId) {
        return mapper.findFamily(familyId);
    }
}
