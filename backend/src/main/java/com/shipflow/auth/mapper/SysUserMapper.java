package com.shipflow.auth.mapper;

import com.shipflow.auth.model.SysUserDO;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface SysUserMapper {

    SysUserDO findPlatformUser(@Param("username") String username);

    SysUserDO findTenantUser(@Param("tenantCode") String tenantCode, @Param("username") String username);
}
