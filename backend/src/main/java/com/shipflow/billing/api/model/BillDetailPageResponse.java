package com.shipflow.billing.api.model;
import java.util.List;
public record BillDetailPageResponse(int page, int pageSize, long totalPages, long total, List<BillDetailResponse> items) { }
