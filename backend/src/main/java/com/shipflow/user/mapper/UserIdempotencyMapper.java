package com.shipflow.user.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

@Mapper
public interface UserIdempotencyMapper {
    Record find(@Param("tenantId") Long tenantId, @Param("operationId") String operationId, @Param("key") String key);
    int insert(@Param("tenantId") Long tenantId, @Param("operationId") String operationId, @Param("key") String key,
               @Param("requestHash") String requestHash, @Param("expiresAt") LocalDateTime expiresAt);
    int complete(@Param("tenantId") Long tenantId, @Param("operationId") String operationId, @Param("key") String key, @Param("resourceId") Long resourceId);
    record Record(String requestHash, Long resourceId, String processingStatus) {}
}
