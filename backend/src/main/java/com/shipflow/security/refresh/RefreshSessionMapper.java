package com.shipflow.security.refresh;

import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface RefreshSessionMapper {

    int insert(RefreshSessionDO session);

    RefreshSessionDO findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    int markActiveAsRotated(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);

    int markExpired(@Param("id") Long id, @Param("updatedAt") LocalDateTime updatedAt);

    int revokeFamily(@Param("familyId") String familyId, @Param("revokedAt") LocalDateTime revokedAt);

    List<RefreshSessionDO> findFamily(@Param("familyId") String familyId);
}
