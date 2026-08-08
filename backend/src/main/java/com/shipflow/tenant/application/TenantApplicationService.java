package com.shipflow.tenant.application;

import com.shipflow.tenant.api.model.CreateTenantRequest;
import com.shipflow.tenant.api.model.StatusChangeRequest;
import com.shipflow.tenant.api.model.UpdateTenantRequest;
import com.shipflow.tenant.domain.model.Tenant;
import com.shipflow.tenant.domain.model.TenantPage;
import com.shipflow.tenant.mapper.TenantAuditMapper;
import com.shipflow.tenant.mapper.TenantIdempotencyMapper;
import com.shipflow.tenant.mapper.TenantMapper;
import com.shipflow.tenant.mapper.TenantProvisioningMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.List;

@Service
@Profile("!test")
public class TenantApplicationService {
    private static final String OPERATION = "createTenant";
    private static final List<String> INITIAL_ADMIN_PERMISSIONS = List.of(
            "store:create", "store:read", "store:manage", "user:read", "user:manage",
            "role:read", "role:manage", "permission:read");
    private final TenantMapper tenantMapper;
    private final TenantProvisioningMapper provisioningMapper;
    private final TenantIdempotencyMapper idempotencyMapper;
    private final TenantAuditMapper auditMapper;
    private final PasswordEncoder passwordEncoder;
    private final Clock clock;

    public TenantApplicationService(TenantMapper tenantMapper, TenantProvisioningMapper provisioningMapper,
                                    TenantIdempotencyMapper idempotencyMapper, TenantAuditMapper auditMapper,
                                    PasswordEncoder passwordEncoder, Clock clock) {
        this.tenantMapper = tenantMapper;
        this.provisioningMapper = provisioningMapper;
        this.idempotencyMapper = idempotencyMapper;
        this.auditMapper = auditMapper;
        this.passwordEncoder = passwordEncoder;
        this.clock = clock;
    }

    @Transactional
    public Tenant create(CreateTenantRequest request, String idempotencyKey, Long operatorUserId, String requestId) {
        if (idempotencyKey == null || idempotencyKey.isBlank() || idempotencyKey.length() > 128) {
            throw new TenantException("COMMON-1001", 400);
        }
        String requestHash = hash(request.tenantCode() + "\n" + request.tenantName() + "\n"
                + request.initialAdmin().username() + "\n" + request.initialAdmin().displayName());
        TenantIdempotencyMapper.IdempotencyRecord prior = idempotencyMapper.find(OPERATION, idempotencyKey);
        if (prior != null) {
            if (!requestHash.equals(prior.requestHash())) throw new TenantException("COMMON-1009", 409);
            if (prior.resourceId() != null) return requireTenant(prior.resourceId());
            throw new TenantException("COMMON-1010", 409);
        }
        try {
            idempotencyMapper.insert(OPERATION, idempotencyKey, requestHash, LocalDateTime.now(clock).plusMinutes(30));
            if (tenantMapper.findByCode(request.tenantCode()) != null) throw new TenantException("TENANT-1001", 409);
            Tenant tenant = new Tenant(null, request.tenantCode(), request.tenantName(), "ACTIVE", 0, null, null);
            tenantMapper.insertTenant(tenant);
            Tenant createdTenant = tenantMapper.findByCode(request.tenantCode());
            if (createdTenant == null) throw new IllegalStateException("Created tenant could not be reloaded");
            provisioningMapper.insertAdminUser(createdTenant.id(), request.initialAdmin().username(),
                    request.initialAdmin().displayName(), passwordEncoder.encode(request.initialAdmin().temporaryPassword()));
            Long userId = provisioningMapper.findUserId(createdTenant.id(), request.initialAdmin().username());
            if (userId == null) throw new IllegalStateException("Initial admin user was not created");
            provisioningMapper.insertAdminRole(createdTenant.id());
            Long roleId = provisioningMapper.findRoleId(createdTenant.id(), "MERCHANT_ADMIN");
            if (roleId == null) throw new IllegalStateException("Tenant admin role was not created");
            List<Long> permissionIds = provisioningMapper.findPermissionIds(INITIAL_ADMIN_PERMISSIONS);
            if (permissionIds == null || permissionIds.size() != INITIAL_ADMIN_PERMISSIONS.size()) {
                throw new IllegalStateException("Tenant permission dictionary is incomplete");
            }
            provisioningMapper.bindAdmin(createdTenant.id(), userId, roleId);
            provisioningMapper.bindRolePermissions(roleId, permissionIds);
            Tenant created = requireTenant(createdTenant.id());
            idempotencyMapper.complete(OPERATION, idempotencyKey, created.id());
            auditMapper.insert(created.id(), operatorUserId, "CREATE", "tenant", created.id(), requestId,
                    "SUCCESS", null, LocalDateTime.now(clock));
            return created;
        } catch (DuplicateKeyException exception) {
            throw new TenantException("TENANT-1001", 409);
        }
    }

    public TenantPage list(String status, String tenantCode, int page, int pageSize) {
        validatePage(page, pageSize);
        long total = tenantMapper.count(status, tenantCode);
        return new TenantPage(page, pageSize, total, (int) ((total + pageSize - 1) / pageSize),
                tenantMapper.findPage(status, tenantCode, (page - 1) * pageSize, pageSize));
    }

    public Tenant get(Long tenantId) { return requireTenant(tenantId); }

    @Transactional
    public Tenant update(Long tenantId, UpdateTenantRequest request, Long operatorUserId, String requestId) {
        if (tenantMapper.updateName(tenantId, request.tenantName(), request.version()) != 1) throw conflictOrNotFound(tenantId);
        Tenant tenant = requireTenant(tenantId);
        auditMapper.insert(tenant.id(), operatorUserId, "UPDATE", "tenant", tenant.id(), requestId, "SUCCESS", null, LocalDateTime.now(clock));
        return tenant;
    }

    @Transactional
    public Tenant changeStatus(Long tenantId, StatusChangeRequest request, Long operatorUserId, String requestId) {
        if (!"ACTIVE".equals(request.status()) && !"DISABLED".equals(request.status())) throw new TenantException("TENANT-1002", 422);
        if (tenantMapper.updateStatus(tenantId, request.status(), request.version()) != 1) throw conflictOrNotFound(tenantId);
        Tenant tenant = requireTenant(tenantId);
        auditMapper.insert(tenant.id(), operatorUserId, "STATUS_CHANGE", "tenant", tenant.id(), requestId, "SUCCESS", null, LocalDateTime.now(clock));
        return tenant;
    }

    private Tenant requireTenant(Long id) { Tenant tenant = tenantMapper.findById(id); if (tenant == null) throw new TenantException("COMMON-1006", 404); return tenant; }
    private TenantException conflictOrNotFound(Long id) { return tenantMapper.findById(id) == null ? new TenantException("COMMON-1006", 404) : new TenantException("COMMON-1005", 409); }
    private void validatePage(int page, int pageSize) { if (page < 1 || pageSize < 1 || pageSize > 100) throw new TenantException("COMMON-1001", 400); }
    private String hash(String value) { try { return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8))); } catch (Exception e) { throw new IllegalStateException("Request hashing failed", e); } }
}
