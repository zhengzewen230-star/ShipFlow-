package com.shipflow.quote.api.model;

import java.util.List;

public record QuotePageResponse(int page, int pageSize, long total, int totalPages, List<QuoteResponse> items) {
}
