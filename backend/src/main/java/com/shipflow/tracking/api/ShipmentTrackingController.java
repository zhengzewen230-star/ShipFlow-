package com.shipflow.tracking.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.order.application.ShipmentOrderException;
import com.shipflow.tracking.api.model.ShipmentTrackingEventResponse;
import com.shipflow.tracking.application.ShipmentTrackingQueryApplicationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/orders/{orderNo}/tracking")
public class ShipmentTrackingController {
    private final ShipmentTrackingQueryApplicationService service;

    public ShipmentTrackingController(ShipmentTrackingQueryApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<List<ShipmentTrackingEventResponse>> list(
            @PathVariable String orderNo, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.list(tenant(jwt), user(jwt), orderNo));
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
