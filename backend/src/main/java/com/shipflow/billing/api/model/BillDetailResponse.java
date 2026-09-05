package com.shipflow.billing.api.model;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
public record BillDetailResponse(Long id, Long batchId, Long providerId, String providerBillDetailNo, int lineNo,
                                 Long shipmentOrderId, String trackingNo, BigDecimal billedAmount, String currency,
                                 String feeType, String detailStatus, String errorMessage, String rawLineMasked, String errorHandlingStatus, OffsetDateTime createdAt,
                                 OffsetDateTime updatedAt) { }
