package com.shipflow.tracking.mapper;

import com.shipflow.tracking.domain.ShipmentTrackingEvent;
import com.shipflow.tracking.domain.ShipmentTrackingOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface ShipmentTrackingMapper {
    ShipmentTrackingOrder findOrderByReference(@Param("tenantId") Long tenantId,
                                                @Param("reference") String reference);

    List<ShipmentTrackingEvent> findTimeline(@Param("tenantId") Long tenantId,
                                             @Param("orderId") Long orderId);

    List<SandboxOrder> findSandboxOrders();

    String findLatestSimulationStatus(@Param("tenantId") Long tenantId,
                                      @Param("orderId") Long orderId);

    int insertSimulationEvent(@Param("tenantId") Long tenantId,
                              @Param("orderId") Long orderId,
                              @Param("orderNo") String orderNo,
                              @Param("waybillNo") String waybillNo,
                              @Param("statusCode") String statusCode,
                              @Param("title") String title,
                              @Param("description") String description,
                              @Param("location") String location,
                              @Param("occurredAt") LocalDateTime occurredAt);

    int transitionOrder(@Param("tenantId") Long tenantId,
                        @Param("orderId") Long orderId,
                        @Param("fromStatus") String fromStatus,
                        @Param("toStatus") String toStatus,
                        @Param("version") Long version);

    record SandboxOrder(Long tenantId, Long orderId, String orderNo, String waybillNo,
                        String currentStatus, Long version, String destinationAddress) {
    }
}
