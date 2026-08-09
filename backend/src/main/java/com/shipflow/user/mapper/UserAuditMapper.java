package com.shipflow.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

@Mapper
public interface UserAuditMapper {
    int insert(@Param("tenantId") Long tenantId, @Param("operatorUserId") Long operatorUserId,
               @Param("actionType") String actionType, @Param("resourceId") Long resourceId,
               @Param("requestId") String requestId, @Param("occurredAt") LocalDateTime occurredAt);
}
