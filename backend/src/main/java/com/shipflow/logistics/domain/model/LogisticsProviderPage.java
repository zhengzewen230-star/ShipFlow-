package com.shipflow.logistics.domain.model;
import java.util.List;
public record LogisticsProviderPage(int page, int pageSize, long total, int totalPages, List<LogisticsProvider> items) { }
