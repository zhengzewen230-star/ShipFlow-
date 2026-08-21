package com.shipflow.tenant.mapper;

import com.shipflow.tenant.domain.model.Tenant;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface TenantMapper {
    int insertTenant(Tenant tenant);
    Tenant findById(@Param("tenantId") Long tenantId);
    Tenant findByCode(@Param("tenantCode") String tenantCode);
    List<Tenant> findPage(@Param("status") String status, @Param("tenantCode") String tenantCode,
                          @Param("offset") int offset, @Param("pageSize") int pageSize);
    long count(@Param("status") String status, @Param("tenantCode") String tenantCode);
    int updateName(@Param("tenantId") Long tenantId, @Param("tenantName") String tenantName, @Param("version") long version);
    int updateStatus(@Param("tenantId") Long tenantId, @Param("status") String status, @Param("version") long version);
}
