package com.shipflow.sf.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.sf.api.model.SfOperation;
import com.shipflow.sf.api.model.SfOperationRequest;
import com.shipflow.sf.api.model.SfOperationResponse;
import com.shipflow.sf.application.SfInternationalService;
import com.shipflow.sf.application.SfIntegrationException;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/orders/{orderId}/sf-international")
public class SfInternationalController {
    private final SfInternationalService service;

    public SfInternationalController(SfInternationalService service) {
        this.service = service;
    }

    @PostMapping("/{operation}")
    public ApiResponse<SfOperationResponse> execute(
            @PathVariable Long orderId,
            @PathVariable String operation,
            @Valid @RequestBody SfOperationRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @AuthenticationPrincipal Jwt jwt) {
        try {
            SfOperation parsed = SfOperation.fromPath(operation);
            String requestId = idempotencyKey == null || idempotencyKey.isBlank()
                    ? SfInternationalService.newRequestId() : idempotencyKey;
            return ApiResponse.success(service.execute(tenant(jwt), orderId, parsed, requestId, request.msgData()));
        } catch (IllegalArgumentException exception) {
            throw new SfIntegrationException("SF-1000", 400, "不支持的顺丰物流操作");
        }
    }

    private Long tenant(Jwt jwt) {
        try {
            return jwt == null ? null : Long.valueOf(jwt.getClaimAsString("tenant_id"));
        } catch (RuntimeException exception) {
            throw new SfIntegrationException("COMMON-1004", 403, "当前账号无权执行顺丰物流操作");
        }
    }
}
