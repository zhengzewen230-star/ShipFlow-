package com.shipflow.store.domain.model;
import com.shipflow.store.api.model.StoreListItem;
import java.util.List;
public record StorePage(int page, int pageSize, long total, int totalPages, List<StoreListItem> items) {}
