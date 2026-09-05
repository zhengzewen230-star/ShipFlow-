package com.shipflow.billing.api;

import com.shipflow.billing.api.model.*;
import com.shipflow.billing.application.*;
import com.shipflow.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/v1")
public class BillingController {
    private final BillingApplicationService service;
    public BillingController(BillingApplicationService service){this.service=service;}
    @PostMapping(value="/billing/import-batches",consumes=MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<BillBatchResponse>> importCsv(@RequestParam Long providerId,@RequestPart("file") MultipartFile file,@RequestHeader("Idempotency-Key") String idempotencyKey,@RequestHeader(value="X-Request-Id",required=false) String requestId,@AuthenticationPrincipal Jwt jwt){return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(service.importCsv(tenant(jwt),user(jwt),providerId,file,idempotencyKey,requestId)));}
    @GetMapping("/billing/import-batches") public ApiResponse<BillBatchPageResponse> batches(@RequestParam(required=false) Long providerId,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.listBatches(tenant(jwt),user(jwt),providerId,status,page,pageSize));}
    @GetMapping("/billing/import-batches/{batchId}") public ApiResponse<BillBatchResponse> batch(@PathVariable Long batchId,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.getBatch(tenant(jwt),user(jwt),batchId));}
    @GetMapping("/billing/import-batches/{batchId}/errors") public ApiResponse<BillDetailPageResponse> errors(@PathVariable Long batchId,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.listErrors(tenant(jwt),user(jwt),batchId,page,pageSize));}
    @GetMapping("/billing/details") public ApiResponse<BillDetailPageResponse> details(@RequestParam(required=false) Long batchId,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.listDetails(tenant(jwt),user(jwt),batchId,status,page,pageSize));}
    @GetMapping("/reconciliations") public ApiResponse<ReconciliationPageResponse> reconciliations(@RequestParam(required=false) Long orderId,@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.listReconciliations(tenant(jwt),user(jwt),orderId,status,page,pageSize));}
    @GetMapping("/reconciliations/{reconciliationId}") public ApiResponse<ReconciliationResponse> reconciliation(@PathVariable Long reconciliationId,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.getReconciliation(tenant(jwt),user(jwt),reconciliationId));}
    @PostMapping("/reconciliations/{reconciliationId}/confirm") public ApiResponse<ReconciliationResponse> confirm(@PathVariable Long reconciliationId,@Valid @RequestBody ReconciliationConfirmRequest request,@RequestHeader("Idempotency-Key") String idempotencyKey,@RequestHeader(value="X-Request-Id",required=false) String requestId,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.confirm(tenant(jwt),user(jwt),reconciliationId,request,idempotencyKey,requestId));}
    @PostMapping("/reconciliations/{reconciliationId}/reject") public ApiResponse<ReconciliationResponse> reject(@PathVariable Long reconciliationId,@Valid @RequestBody ReconciliationActionRequest request,@RequestHeader("Idempotency-Key") String idempotencyKey,@RequestHeader(value="X-Request-Id",required=false) String requestId,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.reject(tenant(jwt),user(jwt),reconciliationId,request,idempotencyKey,requestId));}
    @PostMapping("/reconciliations/{reconciliationId}/comments") public ApiResponse<ReconciliationResponse> comment(@PathVariable Long reconciliationId,@Valid @RequestBody ReconciliationActionRequest request,@RequestHeader("Idempotency-Key") String idempotencyKey,@RequestHeader(value="X-Request-Id",required=false) String requestId,@AuthenticationPrincipal Jwt jwt){return ApiResponse.success(service.comment(tenant(jwt),user(jwt),reconciliationId,request,idempotencyKey,requestId));}
    private Long tenant(Jwt jwt){try{return Long.valueOf(jwt.getClaimAsString("tenant_id"));}catch(Exception e){throw new BillingException("COMMON-1004",403);}}
    private Long user(Jwt jwt){try{return Long.valueOf(jwt.getSubject());}catch(Exception e){throw new BillingException("COMMON-1004",403);}}
}
