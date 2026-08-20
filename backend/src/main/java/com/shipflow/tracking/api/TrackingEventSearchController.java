package com.shipflow.tracking.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.order.application.ShipmentOrderException;
import com.shipflow.tracking.api.model.TrackingEventListPageResponse;
import com.shipflow.tracking.application.TrackingQueryApplicationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;

@RestController
@RequestMapping("/api/v1/tracking-events")
public class TrackingEventSearchController {
    private final TrackingQueryApplicationService service;
    public TrackingEventSearchController(TrackingQueryApplicationService service) { this.service = service; }

    @GetMapping
    public ApiResponse<TrackingEventListPageResponse> page(
            @RequestParam(required = false) String orderNo,
            @RequestParam(required = false) String trackingNo,
            @RequestParam(required = false) String sfTrackingNo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String statusCode,
            @RequestParam(required = false) OffsetDateTime from,
            @RequestParam(required = false) OffsetDateTime eventTimeFrom,
            @RequestParam(required = false) OffsetDateTime to,
            @RequestParam(required = false) OffsetDateTime eventTimeTo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.pageForCaller(tenant(jwt), user(jwt), orderNo,
                trackingNo == null ? sfTrackingNo : trackingNo, status == null ? statusCode : status,
                from == null ? eventTimeFrom : from, to == null ? eventTimeTo : to, page, pageSize, sortDirection));
    }

    private Long tenant(Jwt jwt) { try { return Long.valueOf(jwt.getClaimAsString("tenant_id")); }
        catch (Exception e) { throw new ShipmentOrderException("COMMON-1004", 403); } }
    private Long user(Jwt jwt) { try { return Long.valueOf(jwt.getSubject()); }
        catch (Exception e) { throw new ShipmentOrderException("COMMON-1004", 403); } }
}
