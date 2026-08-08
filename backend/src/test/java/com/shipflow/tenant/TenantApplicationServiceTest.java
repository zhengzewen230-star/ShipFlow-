package com.shipflow.tenant;

import com.shipflow.tenant.api.model.CreateTenantRequest;
import com.shipflow.tenant.api.model.UpdateTenantRequest;
import com.shipflow.tenant.application.TenantApplicationService;
import com.shipflow.tenant.application.TenantException;
import com.shipflow.tenant.domain.model.Tenant;
import com.shipflow.tenant.mapper.TenantAuditMapper;
import com.shipflow.tenant.mapper.TenantIdempotencyMapper;
import com.shipflow.tenant.mapper.TenantMapper;
import com.shipflow.tenant.mapper.TenantProvisioningMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class TenantApplicationServiceTest {
    private TenantMapper tenantMapper;
    private TenantProvisioningMapper provisioning;
    private TenantIdempotencyMapper idempotency;
    private TenantAuditMapper audit;
    private TenantApplicationService service;

    @BeforeEach
    void setUp() {
        tenantMapper = mock(TenantMapper.class);
        provisioning = mock(TenantProvisioningMapper.class);
        idempotency = mock(TenantIdempotencyMapper.class);
        audit = mock(TenantAuditMapper.class);
        service = new TenantApplicationService(tenantMapper, provisioning, idempotency, audit,
                new BCryptPasswordEncoder(10), Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void createsTenantAndProvisioningWithinApplicationFlow() {
        Tenant created = new Tenant(42L, "TENANT_NEW", "New Tenant", "ACTIVE", 0, null, null);
        when(idempotency.find("createTenant", "key-1")).thenReturn(null);
        when(tenantMapper.findByCode("TENANT_NEW")).thenReturn(null, created);
        when(provisioning.findUserId(42L, "admin")).thenReturn(8L);
        when(provisioning.findRoleId(42L, "MERCHANT_ADMIN")).thenReturn(9L);
        when(provisioning.findPermissionIds(anyList())).thenReturn(java.util.List.of(1L, 2L, 3L, 4L, 5L, 6L, 7L, 8L));
        when(tenantMapper.findById(42L)).thenReturn(created);

        Tenant result = service.create(request(), "key-1", 1L, "request-1");

        assertThat(result.id()).isEqualTo(42L);
        verify(provisioning).insertAdminUser(eq(42L), eq("admin"), eq("Admin"), argThat(hash -> hash.startsWith("$2")));
        verify(provisioning).insertAdminRole(42L);
        verify(provisioning).bindAdmin(42L, 8L, 9L);
        verify(provisioning).bindRolePermissions(eq(9L), argThat(ids -> ids.size() == 8));
        verify(idempotency).complete("createTenant", "key-1", 42L);
        verify(audit).insert(eq(42L), eq(1L), eq("CREATE"), eq("tenant"), eq(42L), eq("request-1"), eq("SUCCESS"), isNull(), any());
    }

    @Test
    void replaysSameIdempotencyKeyAndRejectsDifferentBody() {
        Tenant existing = new Tenant(42L, "TENANT_NEW", "New Tenant", "ACTIVE", 0, null, null);
        String hash = "not-the-calculated-hash";
        when(idempotency.find("createTenant", "key-1"))
                .thenReturn(new TenantIdempotencyMapper.IdempotencyRecord(hash, 42L, "SUCCEEDED"));
        assertThatThrownBy(() -> service.create(request(), "key-1", 1L, "request-1"))
                .isInstanceOf(TenantException.class).extracting("code").isEqualTo("COMMON-1009");
        verifyNoInteractions(tenantMapper, provisioning, audit);
    }

    @Test
    void updateRequiresMatchingVersion() {
        when(tenantMapper.updateName(42L, "Changed", 1L)).thenReturn(0);
        when(tenantMapper.findById(42L)).thenReturn(new Tenant(42L, "T", "Old", "ACTIVE", 2, null, null));
        assertThatThrownBy(() -> service.update(42L, new UpdateTenantRequest("Changed", 1L), 1L, null))
                .isInstanceOf(TenantException.class).extracting("code").isEqualTo("COMMON-1005");
    }

    private CreateTenantRequest request() {
        return new CreateTenantRequest("TENANT_NEW", "New Tenant",
                new CreateTenantRequest.InitialAdminRequest("admin", "Admin", "Valid-Admin-2026!"));
    }
}
