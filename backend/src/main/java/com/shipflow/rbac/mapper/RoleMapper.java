package com.shipflow.rbac.mapper;

import com.shipflow.rbac.domain.model.Role;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface RoleMapper {
    List<Role> list(@Param("tenantId") Long tenantId, @Param("status") String status);
    Role find(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);
    List<Long> permissionIds(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId);
    int updatePermissions(@Param("tenantId") Long tenantId, @Param("roleId") Long roleId, @Param("version") long version);
    int deletePermissions(@Param("roleId") Long roleId);
    int bindPermission(@Param("roleId") Long roleId, @Param("permissionId") Long permissionId);
    int permissionCount(@Param("permissionIds") List<Long> permissionIds);
}
