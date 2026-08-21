package com.shipflow.order.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface PriceConfirmationMapper {
    record OrderRecord(Long id, Long tenantId, String orderNo, Long storeId, Long quoteId,
                       String currentStatus, BigDecimal estimatedFee, BigDecimal currentFee,
                       BigDecimal confirmedFee, String currency, BigDecimal chargeableWeight,
                       Long version, LocalDateTime createdAt) { }

    record AdjustmentRecord(Long id, Long orderId, Long storeId, String adjustmentType,
                            BigDecimal beforeAmount, BigDecimal afterAmount, BigDecimal differenceAmount,
                            String currency, String confirmationStatus, LocalDateTime requestedAt,
                            LocalDateTime confirmedAt) { }

    OrderRecord findOrder(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    AdjustmentRecord findAdjustment(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                                    @Param("adjustmentId") Long adjustmentId);
    AdjustmentRecord findLatestAdjustment(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    int markRequested(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                      @Param("adjustmentId") Long adjustmentId, @Param("userId") Long userId,
                      @Param("requestedAt") LocalDateTime requestedAt);
    int confirmOrder(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                     @Param("expectedFee") BigDecimal expectedFee, @Param("version") Long version);
    int confirmAdjustment(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId,
                          @Param("adjustmentId") Long adjustmentId, @Param("expectedFee") BigDecimal expectedFee,
                          @Param("userId") Long userId, @Param("confirmedAt") LocalDateTime confirmedAt);
    int audit(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
              @Param("orderId") Long orderId, @Param("action") String action,
              @Param("requestId") String requestId, @Param("result") String result,
              @Param("reason") String reason, @Param("occurredAt") LocalDateTime occurredAt);
}
