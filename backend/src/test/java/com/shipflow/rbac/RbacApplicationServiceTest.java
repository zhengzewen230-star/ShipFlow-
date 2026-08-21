package com.shipflow.rbac;

import com.shipflow.rbac.application.RbacApplicationService;
import com.shipflow.rbac.api.model.RolePermissionBindingRequest;
import com.shipflow.rbac.domain.model.Role;
import com.shipflow.rbac.domain.model.Permission;
import com.shipflow.rbac.mapper.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RbacApplicationServiceTest {
    @Test void listLoadsTenantRolesAndTheirPermissionsWhenStatusIsAbsent(){
        RoleMapper roles = mock(RoleMapper.class);
        Role role = new Role(9L, 1L, "MERCHANT_ADMIN", "Merchant Admin", "TENANT", "ACTIVE", 0L,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(roles.list(1L, null)).thenReturn(List.of(role));
        when(roles.permissionIds(1L, 9L)).thenReturn(List.of(3L));

        RbacApplicationService service = new RbacApplicationService(
                roles, mock(PermissionMapper.class), mock(RbacAuditMapper.class), Clock.systemUTC());

        assertThat(service.list(1L, null)).singleElement().satisfies(result -> {
            assertThat(result.id()).isEqualTo(9L);
            assertThat(result.permissionIds()).containsExactly(3L);
        });
        verify(roles).list(1L, null);
        verify(roles).permissionIds(1L, 9L);
    }

    @Test void roleCanBeSerializedAsHttpResponseData() throws Exception {
        Role role = new Role(9L, 1L, "MERCHANT_ADMIN", "Merchant Admin", "TENANT", "ACTIVE",
                List.of(3L), 0L, java.time.LocalDateTime.now(), java.time.LocalDateTime.now());

        String json = new ObjectMapper().findAndRegisterModules().writeValueAsString(role);

        assertThat(json).contains("\"id\":9", "\"tenantId\":1", "\"permissionIds\":[3]");
    }

    @Test void unknownRoleIsTenantNotFound(){
        RoleMapper r=mock(RoleMapper.class); when(r.find(1L,9L)).thenReturn(null);
        RbacApplicationService s=new RbacApplicationService(r,mock(PermissionMapper.class),mock(RbacAuditMapper.class),Clock.systemUTC());
        assertThatThrownBy(()->s.replace(1L,9L,new RolePermissionBindingRequest(List.of(2L),0L),7L,"r")).hasMessage("COMMON-1006");
    }

    @Test void tenantRoleCannotBindPlatformOrCallbackPermission(){
        RoleMapper roles = mock(RoleMapper.class);
        PermissionMapper permissions = mock(PermissionMapper.class);
        Role role = new Role(9L, 1L, "MERCHANT_ADMIN", "Merchant Admin", "TENANT", "ACTIVE", 0L,
                java.time.LocalDateTime.now(), java.time.LocalDateTime.now());
        when(roles.find(1L, 9L)).thenReturn(role);
        when(permissions.list()).thenReturn(List.of(
                new Permission(1L, "tenant:create", "Create tenant", "platform only"),
                new Permission(2L, "tracking:callback", "Tracking callback", "server only")));

        RbacApplicationService service = new RbacApplicationService(
                roles, permissions, mock(RbacAuditMapper.class), Clock.systemUTC());

        assertThatThrownBy(() -> service.replace(1L, 9L,
                new RolePermissionBindingRequest(List.of(1L, 2L), 0L), 7L, "request-1"))
                .hasMessage("COMMON-1004");
        verify(roles, never()).updatePermissions(anyLong(), anyLong(), anyLong());
    }

    @Test void permissionDictionaryExposesOnlyTenantAssignablePermissions(){
        PermissionMapper permissions = mock(PermissionMapper.class);
        when(permissions.list()).thenReturn(List.of(
                new Permission(1L, "quote:create", "Create quotes", "tenant"),
                new Permission(2L, "tenant:manage", "Manage tenants", "platform"),
                new Permission(3L, "tracking:callback", "Tracking callback", "server only")));
        RbacApplicationService service = new RbacApplicationService(
                mock(RoleMapper.class), permissions, mock(RbacAuditMapper.class), Clock.systemUTC());

        assertThat(service.permissions()).extracting(Permission::permissionCode).containsExactly("quote:create");
    }
}
