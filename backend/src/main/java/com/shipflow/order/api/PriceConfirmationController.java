package com.shipflow.order.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.order.api.model.PriceConfirmationRequest;
import com.shipflow.order.api.model.PriceConfirmationView;
import com.shipflow.order.application.PriceConfirmationApplicationService;
import com.shipflow.order.application.ShipmentOrderException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/orders/{orderId}")
public class PriceConfirmationController {
    private final PriceConfirmationApplicationService service;

    public PriceConfirmationController(PriceConfirmationApplicationService service) {
        this.service = service;
    }

    @GetMapping("/price-confirmation")
    public ApiResponse<PriceConfirmationView> get(@PathVariable Long orderId,
                                                   @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.get(tenant(jwt), user(jwt), orderId));
    }

    @PostMapping("/price-confirmation-requests")
    public ApiResponse<PriceConfirmationView> request(@PathVariable Long orderId,
                                                       @Valid @RequestBody PriceConfirmationRequest request,
                                                       @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                       @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.request(tenant(jwt), user(jwt), orderId, request, idempotencyKey, requestId));
    }

    @PostMapping("/price-confirmation")
    public ApiResponse<PriceConfirmationView> confirm(@PathVariable Long orderId,
                                                      @Valid @RequestBody PriceConfirmationRequest request,
                                                      @RequestHeader("Idempotency-Key") String idempotencyKey,
                                                      @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                      @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.confirm(tenant(jwt), user(jwt), orderId, request, idempotencyKey, requestId));
    }

    private Long tenant(Jwt jwt) {
        try {
            return jwt == null ? null : Long.valueOf(jwt.getClaimAsString("tenant_id"));
        } catch (Exception exception) {
            throw new ShipmentOrderException("COMMON-1004", 403);
        }
    }

    private Long user(Jwt jwt) {
        try {
            return jwt == null ? null : Long.valueOf(jwt.getSubject());
        } catch (Exception exception) {
            throw new ShipmentOrderException("COMMON-1004", 403);
        }
    }
}
