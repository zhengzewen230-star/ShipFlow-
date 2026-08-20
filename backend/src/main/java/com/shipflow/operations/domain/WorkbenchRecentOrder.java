package com.shipflow.operations.domain;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record WorkbenchRecentOrder(Long orderId, String orderNo, Long storeId, String storeName,
                                   String destination, String orderStatus, String labelStatus,
                                   BigDecimal chargeableWeight, BigDecimal estimatedFee, String currency,
                                   String sfTrackingNo, LocalDateTime updatedAt) {
}
