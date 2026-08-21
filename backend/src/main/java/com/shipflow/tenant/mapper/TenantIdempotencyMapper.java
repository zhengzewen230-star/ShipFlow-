package com.shipflow.tenant.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface TenantIdempotencyMapper {
    IdempotencyRecord find(@Param("operationId") String operationId, @Param("key") String key);
    int insert(@Param("operationId") String operationId, @Param("key") String key, @Param("requestHash") String requestHash,
               @Param("expiresAt") java.time.LocalDateTime expiresAt);
    int complete(@Param("operationId") String operationId, @Param("key") String key, @Param("resourceId") Long resourceId);
    record IdempotencyRecord(String requestHash, Long resourceId, String processingStatus) {}
}
