package com.shipflow.tracking.mapper;

import com.shipflow.tracking.domain.TrackingEvent;
import com.shipflow.tracking.domain.TrackingEventView;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;
import java.time.LocalDateTime;

@Mapper
public interface TrackingQueryMapper {
    boolean orderExists(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);
    Long findOrderStoreId(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

    String currentStatus(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

    List<TrackingEvent> findEvents(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

    TrackingEvent findLatest(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

    long countEvents(@Param("tenantId") Long tenantId, @Param("orderId") Long orderId);

    List<TrackingEventView> findEventPage(@Param("tenantId") Long tenantId,
                                          @Param("orderId") Long orderId,
                                          @Param("offset") int offset,
                                          @Param("limit") int limit);

    long countUnifiedEventsForCaller(@Param("tenantId") Long tenantId,
                                     @Param("userId") Long userId,
                                     @Param("orderNo") String orderNo,
                                     @Param("trackingNo") String trackingNo,
                                     @Param("statusCode") String statusCode,
                                     @Param("from") LocalDateTime from,
                                     @Param("to") LocalDateTime to);

    List<com.shipflow.tracking.domain.TrackingEventListItem> findUnifiedEventsForCaller(
            @Param("tenantId") Long tenantId, @Param("userId") Long userId,
            @Param("orderNo") String orderNo, @Param("trackingNo") String trackingNo,
            @Param("statusCode") String statusCode, @Param("from") LocalDateTime from,
            @Param("to") LocalDateTime to, @Param("sortDirection") String sortDirection,
            @Param("offset") int offset, @Param("limit") int limit);
}
