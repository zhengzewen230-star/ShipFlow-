package com.shipflow.logistics.domain.model;

import java.util.List;

/** Paginated public catalogue. Platform price tiers and supplier configuration are excluded. */
public record PublicLogisticsChannelPage(int page, int pageSize, long total, int totalPages,
                                         List<PublicLogisticsChannel> items) {
}
