package com.shipflow.tenant.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface TenantProvisioningMapper {
    int insertAdminUser(@Param("tenantId") Long tenantId, @Param("username") String username,
                        @Param("displayName") String displayName, @Param("passwordHash") String passwordHash);
    int insertAdminRole(@Param("tenantId") Long tenantId);
    Long findRoleId(@Param("tenantId") Long tenantId, @Param("roleCode") String roleCode);
    int bindAdmin(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("roleId") Long roleId);
    Long findUserId(@Param("tenantId") Long tenantId, @Param("username") String username);
    List<Long> findPermissionIds(@Param("permissionCodes") List<String> permissionCodes);
    int bindRolePermissions(@Param("roleId") Long roleId, @Param("permissionIds") List<Long> permissionIds);
}
