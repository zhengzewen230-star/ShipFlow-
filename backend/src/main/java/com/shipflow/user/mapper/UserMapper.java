package com.shipflow.user.mapper;

import com.shipflow.user.domain.model.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.util.List;

@Mapper
public interface UserMapper {
    int insert(@Param("tenantId") Long tenantId, @Param("username") String username,
               @Param("displayName") String displayName, @Param("passwordHash") String passwordHash);
    User findById(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    User findByUsername(@Param("tenantId") Long tenantId, @Param("username") String username);
    List<User> page(@Param("tenantId") Long tenantId, @Param("status") String status,
                    @Param("username") String username, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long count(@Param("tenantId") Long tenantId, @Param("status") String status, @Param("username") String username);
    int updateName(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                   @Param("displayName") String displayName, @Param("version") long version);
    int updateStatus(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                     @Param("status") String status, @Param("version") long version);
    List<Long> roleIds(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    int deleteRoles(@Param("tenantId") Long tenantId, @Param("userId") Long userId);
    int bindRole(@Param("tenantId") Long tenantId, @Param("userId") Long userId, @Param("roleId") Long roleId);
    int roleCount(@Param("tenantId") Long tenantId, @Param("roleIds") List<Long> roleIds);
}
