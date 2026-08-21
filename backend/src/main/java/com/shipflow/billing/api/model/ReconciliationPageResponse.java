package com.shipflow.billing.api.model;
import java.util.List;
public record ReconciliationPageResponse(int page, int pageSize, long totalPages, long total, List<ReconciliationResponse> items) { }
