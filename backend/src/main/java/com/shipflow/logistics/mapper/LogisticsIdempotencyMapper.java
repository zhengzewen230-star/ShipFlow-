package com.shipflow.logistics.mapper;
import org.apache.ibatis.annotations.*; import java.time.LocalDateTime;
@Mapper public interface LogisticsIdempotencyMapper {
    record Record(String requestHash, Long resourceId) { }
    Record find(@Param("operation") String operation, @Param("idempotencyKey") String key);
    int insert(@Param("operation") String operation, @Param("idempotencyKey") String key, @Param("requestHash") String hash, @Param("expiresAt") LocalDateTime expiresAt);
    int complete(@Param("operation") String operation, @Param("idempotencyKey") String key, @Param("resourceId") Long resourceId);
}
