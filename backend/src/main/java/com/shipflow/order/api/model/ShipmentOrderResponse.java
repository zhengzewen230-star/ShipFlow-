package com.shipflow.order.api.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ShipmentOrderResponse(Long id, String orderNo, Long storeId, Long quoteId, String status,
                                    String destinationCountry, Long channelId, String trackingNo,
                                    BigDecimal estimatedFee, BigDecimal currentFee, BigDecimal confirmedFee,
                                    String currency, BigDecimal chargeableWeight, Long version, OffsetDateTime createdAt) {
    /** Compatibility constructor for callers compiled against the phase-six projection. */
    public ShipmentOrderResponse(Long id, String orderNo, Long quoteId, String status,
                                 BigDecimal estimatedFee, String currency,
                                 BigDecimal chargeableWeight, Long version, OffsetDateTime createdAt) {
        this(id, orderNo, null, quoteId, status, null, null, null, estimatedFee, null, null,
                currency, chargeableWeight, version, createdAt);
    }
}
