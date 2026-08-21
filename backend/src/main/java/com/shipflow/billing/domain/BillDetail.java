package com.shipflow.billing.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record BillDetail(Long id, Long tenantId, Long batchId, Long providerId, String providerBillDetailNo,
                         int lineNo, Long shipmentOrderId, String trackingNo, BigDecimal billedAmount,
                         String currency, String feeType, String detailStatus, String errorMessage,
                         LocalDateTime createdAt, LocalDateTime updatedAt) { }
