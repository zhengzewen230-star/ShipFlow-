package com.shipflow.tenant.application;

import com.shipflow.tenant.domain.model.Tenant;
import com.shipflow.tenant.mapper.TenantIdempotencyMapper;
import com.shipflow.tenant.mapper.TenantMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Reads idempotency completion from a separate transaction so a concurrent
 * request does not remain pinned to its original database snapshot.
 */
@Service
public class TenantIdempotencyLookupService {
    private final TenantIdempotencyMapper idempotencyMapper;
    private final TenantMapper tenantMapper;

    public TenantIdempotencyLookupService(TenantIdempotencyMapper idempotencyMapper, TenantMapper tenantMapper) {
        this.idempotencyMapper = idempotencyMapper;
        this.tenantMapper = tenantMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public TenantIdempotencyMapper.IdempotencyRecord find(String operationId, String key) {
        return idempotencyMapper.find(operationId, key);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
    public CompletedTenant findCompleted(String operationId, String key) {
        TenantIdempotencyMapper.IdempotencyRecord record = idempotencyMapper.find(operationId, key);
        if (record == null || record.resourceId() == null) return null;
        Tenant tenant = tenantMapper.findById(record.resourceId());
        return tenant == null ? null : new CompletedTenant(record, tenant);
    }

    public record CompletedTenant(TenantIdempotencyMapper.IdempotencyRecord record, Tenant tenant) { }
}
