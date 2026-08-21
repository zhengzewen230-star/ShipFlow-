package com.shipflow.billing.api.model;
import java.util.List;
public record BillBatchPageResponse(int page, int pageSize, long totalPages, long total, List<BillBatchResponse> items) { }
