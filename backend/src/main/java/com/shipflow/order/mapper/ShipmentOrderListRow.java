package com.shipflow.order.mapper;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShipmentOrderListRow(Long id, String orderNo, Long storeId, Long quoteId, String status,
                                   String destinationCountry, Long channelId, String trackingNo,
                                   BigDecimal estimatedFee, BigDecimal currentFee, BigDecimal confirmedFee,
                                   String currency, BigDecimal chargeableWeight, Long version,
                                   LocalDateTime createdAt) {
}
