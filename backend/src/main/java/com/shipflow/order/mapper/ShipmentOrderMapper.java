package com.shipflow.order.mapper;

import com.shipflow.order.domain.model.ShipmentOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import com.shipflow.order.domain.model.ShipmentOrderListQuery;

@Mapper
public interface ShipmentOrderMapper {
    ShipmentOrder findById(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    java.util.List<ShipmentOrder> findPage(@Param("tenantId") Long tenantId, @Param("status") String status, @Param("storeId") Long storeId, @Param("offset") int offset, @Param("pageSize") int pageSize);
    long count(@Param("tenantId") Long tenantId, @Param("status") String status, @Param("storeId") Long storeId);
    java.util.List<ShipmentOrder> findPageForCaller(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                                     @Param("status") String status, @Param("workbenchFilter") String workbenchFilter, @Param("storeId") Long storeId,
                                                     @Param("offset") int offset, @Param("pageSize") int pageSize);
    long countForCaller(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                        @Param("status") String status, @Param("workbenchFilter") String workbenchFilter, @Param("storeId") Long storeId);
    java.util.List<ShipmentOrderListRow> findFilteredPageForCaller(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                                                    @Param("query") ShipmentOrderListQuery query, @Param("offset") int offset);
    long countFilteredForCaller(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                @Param("query") ShipmentOrderListQuery query);
    java.util.List<ShipmentOrderListRow> findExportRowsForCaller(@Param("tenantId") Long tenantId, @Param("userId") Long userId,
                                                                   @Param("query") ShipmentOrderListQuery query, @Param("ids") java.util.List<Long> ids);
    java.util.List<com.shipflow.order.api.model.OrderDetailResponse.Address> findAddresses(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    java.util.List<ShipmentOrderPackageRow> findPackages(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    java.util.List<com.shipflow.order.api.model.OrderDetailResponse.Item> findItems(@Param("tenantId") Long tenantId, @Param("packageId") Long packageId);
    java.util.List<com.shipflow.order.api.model.OrderDetailResponse.FeeAdjustment> findFeeAdjustments(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    java.util.List<com.shipflow.order.api.model.OrderDetailResponse.WarehouseRecord> findWarehouseRecords(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    java.util.List<com.shipflow.order.api.model.OrderDetailResponse.TrackingRecord> findTrackingRecords(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    java.util.List<com.shipflow.order.api.model.OrderDetailResponse.ExceptionSummary> findExceptionSummaries(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    java.util.List<com.shipflow.order.api.model.OrderDetailResponse.TimelineEvent> findTimeline(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
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
    int advanceDraftVersion(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId, @Param("version") Long version);
    int deleteItems(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    int insertDraftItem(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId, @Param("itemNo") int itemNo, @Param("item") com.shipflow.order.api.model.CreateShipmentOrderRequest.Item item);
    int transitionStatus(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId, @Param("fromStatus") String fromStatus, @Param("toStatus") String toStatus, @Param("version") Long version);
    int insertAuditAction(@Param("tenantId") Long tenantId, @Param("operatorUserId") Long operatorUserId, @Param("orderId") Long orderId, @Param("action") String action, @Param("requestId") String requestId, @Param("result") String result, @Param("reason") String reason, @Param("occurredAt") LocalDateTime occurredAt);
}
