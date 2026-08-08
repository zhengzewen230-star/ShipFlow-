package com.shipflow.tenant.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.tenant.api.model.CreateTenantRequest;
import com.shipflow.tenant.api.model.StatusChangeRequest;
import com.shipflow.tenant.api.model.UpdateTenantRequest;
import com.shipflow.tenant.application.TenantApplicationService;
import com.shipflow.tenant.domain.model.Tenant;
import com.shipflow.tenant.domain.model.TenantPage;
import jakarta.validation.Valid;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/platform/tenants")
@Profile("!test")
public class TenantController {
    private final TenantApplicationService service;

    public TenantController(TenantApplicationService service) { this.service = service; }

    @PostMapping
    public ResponseEntity<ApiResponse<Tenant>> create(@Valid @RequestBody CreateTenantRequest request,
                                                      @RequestHeader(value = "Idempotency-Key", required = false) String key,
                                                      @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                      @AuthenticationPrincipal Jwt jwt) {
        Tenant tenant = service.create(request, key, userId(jwt), requestId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(tenant));
    }

    @GetMapping
    public ResponseEntity<ApiResponse<TenantPage>> list(@RequestParam(defaultValue = "1") int page,
                                                        @RequestParam(defaultValue = "20") int pageSize,
                                                        @RequestParam(required = false) String status,
                                                        @RequestParam(required = false) String tenantCode) {
        return ResponseEntity.ok(ApiResponse.success(service.list(status, tenantCode, page, pageSize)));
    }

    @GetMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<Tenant>> get(@PathVariable Long tenantId) {
        return ResponseEntity.ok(ApiResponse.success(service.get(tenantId)));
    }

    @PutMapping("/{tenantId}")
    public ResponseEntity<ApiResponse<Tenant>> update(@PathVariable Long tenantId, @Valid @RequestBody UpdateTenantRequest request,
                                                      @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                      @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(service.update(tenantId, request, userId(jwt), requestId)));
    }

    @PostMapping("/{tenantId}/status")
    public ResponseEntity<ApiResponse<Tenant>> status(@PathVariable Long tenantId, @Valid @RequestBody StatusChangeRequest request,
                                                      @RequestHeader(value = "X-Request-Id", required = false) String requestId,
                                                      @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.ok(ApiResponse.success(service.changeStatus(tenantId, request, userId(jwt), requestId)));
    }

    private Long userId(Jwt jwt) { return jwt == null || jwt.getSubject() == null ? null : Long.valueOf(jwt.getSubject()); }
}
