package com.shipflow.exceptioncase.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.exceptioncase.api.model.*;
import com.shipflow.exceptioncase.application.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
public class ExceptionClaimController {
    private final ExceptionClaimApplicationService service;
    public ExceptionClaimController(ExceptionClaimApplicationService service){this.service=service;}

    @GetMapping("/exceptions")
    public ApiResponse<ExceptionCasePageResponse> list(@RequestParam(required=false)Long orderId,
                                                       @RequestParam(required=false)String status,
                                                       @RequestParam(defaultValue="1")int page,
                                                       @RequestParam(defaultValue="20")int pageSize,
                                                       @AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.list(tenant(jwt),orderId,status,page,pageSize));}
    @GetMapping("/exceptions/{exceptionId}")
    public ApiResponse<ExceptionCaseResponse> get(@PathVariable Long exceptionId,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.get(tenant(jwt),exceptionId));}
    @PostMapping("/orders/{orderId}/exceptions")
    public ResponseEntity<ApiResponse<ExceptionCaseResponse>> create(@PathVariable Long orderId,
            @Valid @RequestBody CreateExceptionRequest request,@RequestHeader("Idempotency-Key")String key,
            @RequestHeader(value="X-Request-Id",required=false)String requestId,@AuthenticationPrincipal Jwt jwt){return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createException(tenant(jwt),user(jwt),orderId,request,key,requestId)));}
    @PostMapping("/exceptions/{exceptionId}/assign")
    public ApiResponse<ExceptionCaseResponse> assign(@PathVariable Long exceptionId,@Valid @RequestBody AssignExceptionRequest request,
            @RequestHeader(value="X-Request-Id",required=false)String requestId,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.assign(tenant(jwt),user(jwt),exceptionId,request,requestId));}
    @PostMapping("/exceptions/{exceptionId}/status")
    public ApiResponse<ExceptionCaseResponse> transition(@PathVariable Long exceptionId,@Valid @RequestBody ExceptionStatusRequest request,
            @RequestHeader(value="X-Request-Id",required=false)String requestId,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.transitionException(tenant(jwt),user(jwt),exceptionId,request,requestId));}
    @PostMapping("/exceptions/{exceptionId}/claim")
    public ResponseEntity<ApiResponse<ClaimResponse>> createClaim(@PathVariable Long exceptionId,@Valid @RequestBody CreateClaimRequest request,
            @RequestHeader("Idempotency-Key")String key,@RequestHeader(value="X-Request-Id",required=false)String requestId,
            @AuthenticationPrincipal Jwt jwt){return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createClaim(tenant(jwt),user(jwt),exceptionId,request,key,requestId)));}
    @GetMapping("/claims/{claimId}")
    public ApiResponse<ClaimResponse> getClaim(@PathVariable Long claimId,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.getClaim(tenant(jwt),claimId));}
    @PostMapping("/claims/{claimId}/submit")
    public ApiResponse<ClaimResponse> submit(@PathVariable Long claimId,@Valid @RequestBody ClaimActionRequest request,
            @RequestHeader(value="X-Request-Id",required=false)String requestId,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.submitClaim(tenant(jwt),user(jwt),claimId,request,requestId));}
    @PostMapping("/claims/{claimId}/result")
    public ApiResponse<ClaimResponse> result(@PathVariable Long claimId,@Valid @RequestBody ClaimResultRequest request,
            @RequestHeader(value="X-Request-Id",required=false)String requestId,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.resolveClaim(tenant(jwt),user(jwt),claimId,request,requestId));}
    @PostMapping("/claims/{claimId}/close")
    public ApiResponse<ClaimResponse> close(@PathVariable Long claimId,@Valid @RequestBody ClaimActionRequest request,
            @RequestHeader(value="X-Request-Id",required=false)String requestId,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.closeClaim(tenant(jwt),user(jwt),claimId,request,requestId));}

    private Long tenant(Jwt jwt){try{return Long.valueOf(jwt.getClaimAsString("tenant_id"));}catch(Exception e){throw new ExceptionClaimException("COMMON-1004",403);}}
    private Long user(Jwt jwt){try{return Long.valueOf(jwt.getSubject());}catch(Exception e){throw new ExceptionClaimException("COMMON-1004",403);}}
}
