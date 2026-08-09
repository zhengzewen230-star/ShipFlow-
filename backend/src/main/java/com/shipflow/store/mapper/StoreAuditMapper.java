package com.shipflow.store.mapper;
import org.apache.ibatis.annotations.*; import java.time.LocalDateTime;
@Mapper public interface StoreAuditMapper { int insert(@Param("tenantId") Long t,@Param("operatorUserId") Long u,@Param("actionType") String a,@Param("resourceId") Long r,@Param("requestId") String q,@Param("occurredAt") LocalDateTime d); }
