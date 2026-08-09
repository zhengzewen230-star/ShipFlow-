package com.shipflow.store.domain.model;
import java.util.List;
public record StorePage(int page, int pageSize, long total, int totalPages, List<Store> items) {}
