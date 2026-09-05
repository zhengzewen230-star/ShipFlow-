package com.shipflow.operations.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.operations.application.OperationsQueryApplicationService;
import com.shipflow.operations.domain.OperationsSummary;
import com.shipflow.operations.domain.OperationsTodos;
import com.shipflow.operations.domain.OperationsWorkbenchQuery;
import com.shipflow.operations.api.model.OperationsWorkbenchResponse;
import com.shipflow.operations.api.model.OperationsMetricDrilldownPageResponse;
import com.shipflow.order.application.ShipmentOrderException;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.OffsetDateTime;
import java.time.format.DateTimeParseException;

@RestController
@RequestMapping("/api/v1/operations")
public class OperationsController {
    private final OperationsQueryApplicationService service;
    public OperationsController(OperationsQueryApplicationService service) { this.service = service; }
    @GetMapping("/summary") public ApiResponse<OperationsSummary> summary(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(service.summary(tenant(jwt))); }
    @GetMapping("/todos") public ApiResponse<OperationsTodos> todos(@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(service.todos(tenant(jwt))); }

    @GetMapping("/workbench")
    public ApiResponse<OperationsWorkbenchResponse> workbench(@AuthenticationPrincipal Jwt jwt,
            @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) String from,
            @RequestParam(required = false) String to,
            @RequestParam(required = false) String storeId,
            @RequestParam(required = false) String page,
            @RequestParam(required = false) String pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false) String recentLimit,
            @RequestParam(required = false) String riskLimit) {
        return ApiResponse.success(service.workbench(tenant(jwt), user(jwt), new OperationsWorkbenchQuery(
                timeRange, parseOffset(from), parseOffset(to), parseLong(storeId), parseInt(page), parseInt(pageSize),
                sortBy, sortDirection, parseInt(recentLimit), parseInt(riskLimit))));
    }

    @GetMapping("/workbench/metrics/{metricKey}/items")
    public ApiResponse<OperationsMetricDrilldownPageResponse> metricItems(@PathVariable String metricKey,
            @AuthenticationPrincipal Jwt jwt, @RequestParam(required = false) String timeRange,
            @RequestParam(required = false) String from, @RequestParam(required = false) String to,
            @RequestParam(required = false) String storeId, @RequestParam(required = false) String page,
            @RequestParam(required = false) String pageSize) {
        return ApiResponse.success(service.metricDrilldown(tenant(jwt), user(jwt), metricKey,
                new OperationsWorkbenchQuery(timeRange, parseOffset(from), parseOffset(to), parseLong(storeId),
                        parseInt(page), parseInt(pageSize), null, null, null, null)));
    }

    private Long user(Jwt jwt) {
        try { return Long.valueOf(jwt.getSubject()); }
        catch (Exception exception) { throw new ShipmentOrderException("COMMON-1004", 403); }
    }

    private Long parseLong(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Long.valueOf(value); } catch (NumberFormatException exception) { throw badRequest(); }
    }

    private Integer parseInt(String value) {
        if (value == null || value.isBlank()) return null;
        try { return Integer.valueOf(value); } catch (NumberFormatException exception) { throw badRequest(); }
    }

    private OffsetDateTime parseOffset(String value) {
        if (value == null || value.isBlank()) return null;
        try { return OffsetDateTime.parse(value); } catch (DateTimeParseException exception) { throw badRequest(); }
    }

    private ShipmentOrderException badRequest() { return new ShipmentOrderException("COMMON-1001", 400); }

    private Long tenant(Jwt jwt) { try { return Long.valueOf(jwt.getClaimAsString("tenant_id")); } catch (Exception exception) { throw new ShipmentOrderException("COMMON-1004", 403); } }
}
