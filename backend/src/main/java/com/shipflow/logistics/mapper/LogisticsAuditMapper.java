package com.shipflow.logistics.mapper;
import org.apache.ibatis.annotations.*; import java.time.LocalDateTime;
@Mapper public interface LogisticsAuditMapper {
    int insert(@Param("operatorUserId") Long operatorUserId, @Param("actionType") String actionType,
               @Param("resourceType") String resourceType, @Param("resourceId") Long resourceId,
               @Param("requestId") String requestId, @Param("occurredAt") LocalDateTime occurredAt);
    int insertRejection(@Param("tenantId") Long tenantId, @Param("operatorUserId") Long operatorUserId,
                        @Param("actionType") String actionType, @Param("resourceType") String resourceType,
                        @Param("resourceId") Long resourceId, @Param("requestId") String requestId,
                        @Param("reason") String reason);
}
