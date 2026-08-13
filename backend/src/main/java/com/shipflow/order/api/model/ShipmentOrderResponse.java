package com.shipflow.order.api.model;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

public record ShipmentOrderResponse(Long id, String orderNo, Long quoteId, String status,
                                    BigDecimal estimatedFee, String currency,
                                    BigDecimal chargeableWeight, Long version, OffsetDateTime createdAt) { }
