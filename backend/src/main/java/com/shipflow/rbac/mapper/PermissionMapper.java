package com.shipflow.rbac.mapper;

import com.shipflow.rbac.domain.model.Permission;
import org.apache.ibatis.annotations.Mapper;
import java.util.List;

@Mapper
public interface PermissionMapper { List<Permission> list(); }
