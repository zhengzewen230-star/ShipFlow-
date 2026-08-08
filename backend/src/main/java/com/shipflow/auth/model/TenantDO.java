package com.shipflow.auth.model;

/** Minimal tenant projection required during login identity lookup. */
public record TenantDO(Long id, String tenantCode, String status) {
}
