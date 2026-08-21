package com.shipflow.warehouse.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.warehouse.api.model.WarehouseWorkItemResponse;
import com.shipflow.warehouse.api.model.WarehouseWorkPageResponse;
import com.shipflow.warehouse.application.WarehouseWorkApplicationService;
import com.shipflow.warehouse.application.WarehouseException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouse/orders")
public class WarehouseWorkController {
    private final WarehouseWorkApplicationService service;

    public WarehouseWorkController(WarehouseWorkApplicationService service) {
        this.service = service;
    }

    @GetMapping
    public ApiResponse<WarehouseWorkPageResponse> list(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String orderNo,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.list(tenant(jwt), status, orderNo, page, pageSize));
    }

    @GetMapping("/{orderId}")
    public ApiResponse<WarehouseWorkItemResponse> get(@PathVariable Long orderId,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.get(tenant(jwt), orderId));
    }

    private Long tenant(Jwt jwt) {
        try {
            return jwt == null ? null : Long.valueOf(jwt.getClaimAsString("tenant_id"));
        } catch (RuntimeException exception) {
            throw new WarehouseException("COMMON-1004", 403);
        }
    }
}
