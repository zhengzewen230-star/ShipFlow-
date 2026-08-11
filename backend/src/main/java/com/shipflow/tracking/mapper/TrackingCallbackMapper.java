package com.shipflow.tracking.mapper;

import com.shipflow.tracking.domain.ExistingTrackingEvent;
import com.shipflow.tracking.domain.TrackingCallbackOrder;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.time.LocalDateTime;

@Mapper
public interface TrackingCallbackMapper {
    Long authorizedProviderId(@Param("providerCode") String providerCode,
                              @Param("systemUserId") Long systemUserId);

    TrackingCallbackOrder lockOrder(@Param("providerId") Long providerId,
                                    @Param("trackingNo") String trackingNo);

    ExistingTrackingEvent findEvent(@Param("providerId") Long providerId,
                                    @Param("trackingNo") String trackingNo,
                                    @Param("eventId") String eventId);

    int insertEvent(@Param("tenantId") Long tenantId,
                    @Param("providerId") Long providerId,
                    @Param("orderId") Long orderId,
                    @Param("trackingNo") String trackingNo,
                    @Param("eventId") String eventId,
                    @Param("eventCode") String eventCode,
                    @Param("description") String description,
                    @Param("eventTime") LocalDateTime eventTime,
                    @Param("receivedTime") LocalDateTime receivedTime,
                    @Param("rawPayload") String rawPayload,
                    @Param("processStatus") String processStatus,
                    @Param("processMessage") String processMessage);

    int advanceOrder(@Param("tenantId") Long tenantId,
                     @Param("orderId") Long orderId,
                     @Param("fromStatus") String fromStatus,
                     @Param("toStatus") String toStatus);

    int insertAudit(@Param("tenantId") Long tenantId,
                    @Param("systemUserId") Long systemUserId,
                    @Param("orderId") Long orderId,
                    @Param("requestId") String requestId,
                    @Param("detail") String detail,
                    @Param("occurredAt") LocalDateTime occurredAt);
}
