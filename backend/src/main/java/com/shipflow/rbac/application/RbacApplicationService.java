package com.shipflow.rbac.application;

import com.shipflow.rbac.api.model.RolePermissionBindingRequest;
import com.shipflow.rbac.domain.model.*;
import com.shipflow.rbac.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class RbacApplicationService {
    private static final Set<String> TENANT_ASSIGNABLE_PERMISSIONS = Set.of(
            "store:create", "store:read", "store:manage", "user:read", "user:manage",
            "role:read", "role:manage", "permission:read", "logistics:read",
            "quote:read", "quote:create", "quote:validate", "order:read", "order:create",
            "order:manage", "order:price-request", "order:price-confirm", "tracking:read", "exception:read",
            "exception:manage", "billing:read", "finance:bill-import", "finance:reconcile",
            "warehouse:manage", "operations:read", "audit:read");
    private final RoleMapper roles; private final PermissionMapper permissions; private final RbacAuditMapper audit; private final Clock clock;
    public RbacApplicationService(RoleMapper roles,PermissionMapper permissions,RbacAuditMapper audit,Clock clock){this.roles=roles;this.permissions=permissions;this.audit=audit;this.clock=clock;}
    public List<Role> list(Long t,String status){return roles.list(t,status).stream().map(r->withPermissions(t,r)).toList();}
    public Role get(Long t,Long id){Role role=required(t,id);if(!"ACTIVE".equals(role.status()))throw error("COMMON-1006",404);return withPermissions(t,role);}
    public List<Permission> permissions(){return permissions.list().stream().filter(this::tenantAssignable).toList();}
    @Transactional public Role replace(Long t,Long id,RolePermissionBindingRequest r,Long op,String req){
        Role role=required(t,id);if(!"ACTIVE".equals(role.status()))throw error("ROLE-1002",422);List<Long> ids=r.permissionIds().stream().distinct().toList();
        Map<Long, Permission> dictionary = permissions.list().stream().collect(Collectors.toMap(Permission::id, Function.identity()));
        if(ids.stream().anyMatch(permissionId -> !dictionary.containsKey(permissionId)))throw error("ROLE-1002",422);
        if(ids.stream().map(dictionary::get).anyMatch(permission -> !tenantAssignable(permission)))throw error("COMMON-1004",403);
        if(roles.updatePermissions(t,id,r.version())!=1)throw roles.find(t,id)==null?error("COMMON-1006",404):error("COMMON-1005",409);
        roles.deletePermissions(id);ids.forEach(p->roles.bindPermission(id,p));audit.insert(t,op,"PERMISSION_BIND",id,req,LocalDateTime.now(clock));return withPermissions(t,required(t,id));
    }
    private int permissionsCount(List<Long> ids){return ids.isEmpty()?0:roles.permissionCount(ids);}
    private boolean tenantAssignable(Permission permission){return permission != null && TENANT_ASSIGNABLE_PERMISSIONS.contains(permission.permissionCode());}
    private Role required(Long t,Long id){Role r=roles.find(t,id);if(r==null)throw error("COMMON-1006",404);return r;}
    private Role withPermissions(Long t,Role r){return new Role(r.id(),r.tenantId(),r.roleCode(),r.roleName(),r.roleScope(),r.status(),roles.permissionIds(t,r.id()),r.version(),r.createdAt(),r.updatedAt());}
    private RbacException error(String c,int s){return new RbacException(c,s);}
}
