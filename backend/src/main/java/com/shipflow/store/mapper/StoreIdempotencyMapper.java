package com.shipflow.store.mapper;
import org.apache.ibatis.annotations.*; import java.time.LocalDateTime;
@Mapper public interface StoreIdempotencyMapper { Record find(@Param("tenantId") Long t,@Param("operationId") String o,@Param("key") String k); int insert(@Param("tenantId") Long t,@Param("operationId") String o,@Param("key") String k,@Param("requestHash") String h,@Param("expiresAt") LocalDateTime e); int complete(@Param("tenantId") Long t,@Param("operationId") String o,@Param("key") String k,@Param("resourceId") Long r); record Record(String requestHash,Long resourceId){} }
