package com.shipflow.auth.mapper;

import com.shipflow.auth.model.UserAuthorityView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

@Mapper
public interface UserAuthorityMapper {

    List<UserAuthorityView> findActiveAuthorities(
            @Param("userId") Long userId,
            @Param("tenantId") Long tenantId,
            @Param("roleScope") String roleScope);
}
