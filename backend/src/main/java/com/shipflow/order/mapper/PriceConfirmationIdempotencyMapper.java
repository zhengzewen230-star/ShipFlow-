package com.shipflow.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface PriceConfirmationIdempotencyMapper {
    record Record(String requestHash, Long resourceId, String processingStatus) { }

    Record find(@Param("tenantId") Long tenantId, @Param("operationId") String operationId,
                @Param("idempotencyKey") String idempotencyKey);
    int insert(@Param("tenantId") Long tenantId, @Param("operationId") String operationId,
               @Param("idempotencyKey") String idempotencyKey, @Param("requestPath") String requestPath,
               @Param("requestHash") String requestHash, @Param("expiresAt") LocalDateTime expiresAt);
    int complete(@Param("tenantId") Long tenantId, @Param("operationId") String operationId,
                 @Param("idempotencyKey") String idempotencyKey, @Param("resourceId") Long resourceId);
}
