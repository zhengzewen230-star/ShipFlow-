package com.shipflow.tracking.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.order.application.ShipmentOrderException;
import com.shipflow.tracking.api.model.TrackingPageResponse;
import com.shipflow.tracking.application.TrackingQueryApplicationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/tracking-events")
public class TrackingEventsController {
    private final TrackingQueryApplicationService service;

    public TrackingEventsController(TrackingQueryApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<TrackingPageResponse> list(
            @PathVariable Long orderId,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.page(tenant(jwt), user(jwt), orderId, page, pageSize));
    }

    private Long tenant(Jwt jwt) {
        try {
            return jwt == null ? null : Long.valueOf(jwt.getClaimAsString("tenant_id"));
        } catch (Exception exception) {
            throw new ShipmentOrderException("COMMON-1004", 403);
        }
    }

    private Long user(Jwt jwt) {
        try { return jwt == null ? null : Long.valueOf(jwt.getSubject()); }
        catch (Exception exception) { throw new ShipmentOrderException("COMMON-1004", 403); }
    }
}
