package com.shipflow.operations.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.operations.application.OperationsQueryApplicationService;
import com.shipflow.operations.domain.OperationsSummary;
import com.shipflow.operations.domain.OperationsTodos;
import com.shipflow.order.application.ShipmentOrderException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {
    private final OperationsQueryApplicationService service;
    public OperationsController(OperationsQueryApplicationService service) { this.service = service; }
    @GetMapping("/summary") public ApiResponse<OperationsSummary> summary(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(service.summary(tenant(jwt))); }
    @GetMapping("/todos") public ApiResponse<OperationsTodos> todos(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(service.todos(tenant(jwt))); }
    private Long tenant(Jwt jwt) { try { return Long.valueOf(jwt.getClaimAsString("tenant_id")); } catch (Exception exception) { throw new ShipmentOrderException("COMMON-1004", 403); } }
}
