package com.shipflow.order.mapper;

import com.shipflow.order.domain.model.ShipmentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
public interface ShipmentOrderMapper {
    ShipmentOrder findById(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    java.util.List<ShipmentOrder> findPage(@Param("tenantId") Long tenantId, @Param("status") String status, @Param("storeId") Long storeId, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long count(@Param("tenantId") Long tenantId, @Param("status") String status, @Param("storeId") Long storeId);
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
    int updateAddresses(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId, @Param("sender") com.shipflow.order.api.model.CreateShipmentOrderRequest.Address sender, @Param("receiver") com.shipflow.order.api.model.CreateShipmentOrderRequest.Address receiver);
    int deleteItems(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    int insertDraftItem(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId, @Param("itemNo") int itemNo, @Param("item") com.shipflow.order.api.model.CreateShipmentOrderRequest.Item item);
    int transitionStatus(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus, @Param("version") Long version);
    int insertAuditAction(@Param("tenantId") Long tenantId, @Param("operatorUserId") Long operatorUserId, @Param("orderId") Long orderId, @Param("action") String action, @Param("requestId") String requestId, @Param("result") String result, @Param("reason") String reason, @Param("occurredAt") LocalDateTime occurredAt);
}
