package com.shipflow.order.mapper;

import com.shipflow.order.domain.model.ShipmentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface ShipmentOrderMapper {
    ShipmentOrder findByIdempotencyKey(@Param("tenantId") Long tenantId, @Param("idempotencyKey") String idempotencyKey);
    ShipmentOrder findByQuoteId(@Param("tenantId") Long tenantId, @Param("quoteId") Long quoteId);
    int insertOrder(ShipmentOrder order);
    int insertSnapshot(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId, @Param("quote") com.shipflow.quote.domain.model.Quote quote,
                       @Param("volumeDivisor") BigDecimal volumeDivisor, @Param("roundingMode") String roundingMode, @Param("roundingIncrement") BigDecimal roundingIncrement);
    int insertAddress(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId, @Param("type") String type,
                      @Param("address") com.shipflow.order.api.model.CreateShipmentOrderRequest.Address address);
    int insertPackage(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId, @Param("packageNo") String packageNo,
                      @Param("order") ShipmentOrder order);
    Long findPackageId(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    int insertItem(@Param("tenantId") Long tenantId, @Param("packageId") Long packageId, @Param("itemNo") int itemNo,
                   @Param("item") com.shipflow.order.api.model.CreateShipmentOrderRequest.Item item);
    int insertAudit(@Param("tenantId") Long tenantId, @Param("operatorUserId") Long operatorUserId,
                    @Param("orderId") Long orderId, @Param("requestId") String requestId, @Param("occurredAt") LocalDateTime occurredAt);
}
