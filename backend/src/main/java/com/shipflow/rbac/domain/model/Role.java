package com.shipflow.rbac.domain.model;

import java.time.LocalDateTime;
import java.util.List;

public final class Role {
    private final Long id; private final Long tenantId; private final String roleCode; private final String roleName; private final String roleScope; private final String status; private final long version; private final LocalDateTime createdAt; private final LocalDateTime updatedAt; private final List<Long> permissionIds;
    public Role(Long id, Long tenantId, String roleCode, String roleName, String roleScope, String status, long version, LocalDateTime createdAt, LocalDateTime updatedAt){this(id,tenantId,roleCode,roleName,roleScope,status,List.of(),version,createdAt,updatedAt);}
    public Role(Long id, Long tenantId, String roleCode, String roleName, String roleScope, String status, List<Long> permissionIds, long version, LocalDateTime createdAt, LocalDateTime updatedAt){this.id=id;this.tenantId=tenantId;this.roleCode=roleCode;this.roleName=roleName;this.roleScope=roleScope;this.status=status;this.permissionIds=permissionIds==null?List.of():List.copyOf(permissionIds);this.version=version;this.createdAt=createdAt;this.updatedAt=updatedAt;}
    public Long id(){return id;} public Long tenantId(){return tenantId;} public String roleCode(){return roleCode;} public String roleName(){return roleName;} public String roleScope(){return roleScope;} public String status(){return status;} public List<Long> permissionIds(){return permissionIds;} public long version(){return version;} public LocalDateTime createdAt(){return createdAt;} public LocalDateTime updatedAt(){return updatedAt;}
    public Long getId(){return id;} public Long getTenantId(){return tenantId;} public String getRoleCode(){return roleCode;} public String getRoleName(){return roleName;} public String getRoleScope(){return roleScope;} public String getStatus(){return status;} public List<Long> getPermissionIds(){return permissionIds;} public long getVersion(){return version;} public LocalDateTime getCreatedAt(){return createdAt;} public LocalDateTime getUpdatedAt(){return updatedAt;}
}
