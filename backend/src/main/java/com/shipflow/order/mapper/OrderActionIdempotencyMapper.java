package com.shipflow.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.time.LocalDateTime;

@Mapper
public interface OrderActionIdempotencyMapper {
    record Record(String requestHash, Long resourceId, String processingStatus) { }
    Record find(@Param("tenantId") Long tenantId, @Param("operation") String operation, @Param("key") String key);
    int insert(@Param("tenantId") Long tenantId, @Param("operation") String operation, @Param("key") String key,
               @Param("requestHash") String requestHash, @Param("path") String path, @Param("expiresAt") LocalDateTime expiresAt);
    int complete(@Param("tenantId") Long tenantId, @Param("operation") String operation, @Param("key") String key,
                 @Param("orderId") Long orderId);
}
