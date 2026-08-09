package com.shipflow.rbac;

import com.shipflow.rbac.application.RbacApplicationService;
import com.shipflow.rbac.api.model.RolePermissionBindingRequest;
import com.shipflow.rbac.mapper.*;
import org.junit.jupiter.api.Test;
import java.time.Clock;
import java.util.List;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class RbacApplicationServiceTest {
    @Test void unknownRoleIsTenantNotFound(){
        RoleMapper r=mock(RoleMapper.class); when(r.find(1L,9L)).thenReturn(null);
        RbacApplicationService s=new RbacApplicationService(r,mock(PermissionMapper.class),mock(RbacAuditMapper.class),Clock.systemUTC());
        assertThatThrownBy(()->s.replace(1L,9L,new RolePermissionBindingRequest(List.of(2L),0L),7L,"r")).hasMessage("COMMON-1006");
    }
}
