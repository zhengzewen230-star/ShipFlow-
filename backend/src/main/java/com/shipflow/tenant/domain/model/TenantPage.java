package com.shipflow.tenant.domain.model;

import java.util.List;

public record TenantPage(int page, int pageSize, long total, int totalPages, List<Tenant> items) {
}
