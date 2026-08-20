package com.shipflow.order.domain.model;

import java.time.LocalDateTime;

/** Internal, validated query model. Its sort values are mapped in Mapper XML, never interpolated from user input. */
public record ShipmentOrderListQuery(
        String orderNo, Long storeId, String status, String destinationCountry, Long channelId,
        String trackingNo, LocalDateTime createdFrom, LocalDateTime createdTo,
        int page, int pageSize, String sortBy, String sortDirection) {
}
