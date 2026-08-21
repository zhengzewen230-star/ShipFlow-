package com.shipflow.billing.api.model;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record ReconciliationResponse(Long id, Long shipmentOrderId, Long billDetailId, BigDecimal systemAmount,
                                     BigDecimal billedAmount, BigDecimal differenceAmount, String reconciliationStatus,
                                     String resolutionType, Long confirmedBy, OffsetDateTime confirmedAt, String remark,
                                     Long version, OffsetDateTime createdAt, OffsetDateTime updatedAt) { }
