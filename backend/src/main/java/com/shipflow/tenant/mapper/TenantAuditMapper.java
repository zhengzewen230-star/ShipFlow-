package com.shipflow.tenant.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

@Mapper
public interface TenantAuditMapper {
    int insert(@Param("tenantId") Long tenantId, @Param("operatorUserId") Long operatorUserId,
               @Param("actionType") String actionType, @Param("resourceType") String resourceType,
               @Param("resourceId") Long resourceId, @Param("requestId") String requestId,
               @Param("resultStatus") String resultStatus, @Param("reason") String reason,
               @Param("occurredAt") LocalDateTime occurredAt);
}
