package com.shipflow.billing.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record ReconciliationRecord(Long id, Long tenantId, Long shipmentOrderId, Long billDetailId,
                                   BigDecimal systemAmount, BigDecimal billedAmount, BigDecimal differenceAmount,
                                   String reconciliationStatus, String resolutionType, Long confirmedBy,
                                   LocalDateTime confirmedAt, String remark, Long version, LocalDateTime createdAt,
                                   LocalDateTime updatedAt) { }
