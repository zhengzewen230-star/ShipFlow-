package com.shipflow.store.mapper;
import com.shipflow.store.api.model.StoreAuditSummary; import org.apache.ibatis.annotations.*; import java.time.LocalDateTime; import java.util.List;
@Mapper public interface StoreAuditMapper { int insert(@Param("tenantId") Long t,@Param("operatorUserId") Long u,@Param("actionType") String a,@Param("resourceId") Long r,@Param("requestId") String q,@Param("occurredAt") LocalDateTime d); List<StoreAuditSummary> findRecent(@Param("tenantId") Long t,@Param("storeId") Long r,@Param("limit") int n); }
