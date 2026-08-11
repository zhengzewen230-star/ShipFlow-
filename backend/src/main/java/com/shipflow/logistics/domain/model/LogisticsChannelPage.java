package com.shipflow.logistics.domain.model;
import java.util.List;
public record LogisticsChannelPage(int page, int pageSize, long total, int totalPages, List<LogisticsChannel> items) { }
