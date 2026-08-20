package com.shipflow.warehouse.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.warehouse.api.model.WarehouseOverviewResponse;
import com.shipflow.warehouse.application.WarehouseOverviewApplicationService;
import com.shipflow.warehouse.application.WarehouseException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/warehouse")
public class WarehouseOverviewController {
    private final WarehouseOverviewApplicationService service;

    public WarehouseOverviewController(WarehouseOverviewApplicationService service) {
        this.service = service;
    }

    @GetMapping("/overview")
    public ApiResponse<WarehouseOverviewResponse> overview(@AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(WarehouseOverviewResponse.from(service.overview(tenant(jwt))));
    }

    private Long tenant(Jwt jwt) {
        try {
            return jwt == null ? null : Long.valueOf(jwt.getClaimAsString("tenant_id"));
        } catch (RuntimeException exception) {
            throw new WarehouseException("COMMON-1004", 403);
        }
    }
}
