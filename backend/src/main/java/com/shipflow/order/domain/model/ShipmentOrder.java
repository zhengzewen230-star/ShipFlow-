package com.shipflow.order.domain.model;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ShipmentOrder(Long id, Long tenantId, String orderNo, String idempotencyKey, Long storeId,
                            Long quoteId, Long channelId, String currentStatus, String originCountry,
                            String destinationCountry, BigDecimal declaredWeight, BigDecimal declaredLength,
                            BigDecimal declaredWidth, BigDecimal declaredHeight, BigDecimal volumeWeight,
                            BigDecimal chargeableWeight, BigDecimal estimatedFee, String currency,
                            Long version, LocalDateTime createdAt) { }
