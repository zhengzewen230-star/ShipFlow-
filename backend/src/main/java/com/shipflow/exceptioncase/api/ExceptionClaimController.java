package com.shipflow.exceptioncase.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.exceptioncase.api.model.*;
import com.shipflow.exceptioncase.application.ExceptionClaimApplicationService;
import com.shipflow.exceptioncase.application.ExceptionClaimException;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.List;

@RestController
@RequestMapping("/api/v1")
public class ExceptionClaimController {
    private final ExceptionClaimApplicationService service;
    public ExceptionClaimController(ExceptionClaimApplicationService service) { this.service = service; }

    @GetMapping("/exceptions")
    public ApiResponse<ExceptionCasePageResponse> list(@RequestParam(required = false) Long orderId,
                                                       @RequestParam(required = false) String status,
                                                       @RequestParam(required = false) String workbenchFilter,
                                                       @RequestParam(required = false) String exceptionType,
                                                       @RequestParam(required = false) String orderNo,
                                                       @RequestParam(required = false) Long storeId,
                                                       @RequestParam(required = false) String responsibleParty,
                                                       @RequestParam(required = false) OffsetDateTime createdFrom,
                                                       @RequestParam(required = false) OffsetDateTime createdTo,
                                                       @RequestParam(defaultValue = "1") int page,
                                                       @RequestParam(defaultValue = "20") int pageSize,
                                                       @RequestParam(defaultValue = "createdAt") String sortBy,
                                                       @RequestParam(defaultValue = "DESC") String sortDirection,
                                                       @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.list(tenant(jwt), user(jwt), orderId, status, workbenchFilter,
                exceptionType, orderNo, storeId, responsibleParty, createdFrom, createdTo,
                page, pageSize, sortBy, sortDirection));
    }

    @GetMapping("/exceptions/{exceptionId}")
    public ApiResponse<ExceptionCaseResponse> get(@PathVariable Long exceptionId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.get(tenant(jwt), user(jwt), exceptionId));
    }

    @PostMapping("/orders/{orderId}/exceptions")
    public ResponseEntity<ApiResponse<ExceptionCaseResponse>> create(@PathVariable Long orderId,
            @Valid @RequestBody CreateExceptionRequest request, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createException(tenant(jwt), user(jwt), orderId, request, key, requestId)));
    }

    @PostMapping("/exceptions/{exceptionId}/assign")
    public ApiResponse<ExceptionCaseResponse> assign(@PathVariable Long exceptionId, @Valid @RequestBody AssignExceptionRequest request,
            @RequestHeader("Idempotency-Key") String key, @RequestHeader(value = "X-Request-Id", required = false) String requestId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.assign(tenant(jwt), user(jwt), exceptionId, request, key, requestId));
    }

    @PostMapping("/exceptions/{exceptionId}/status")
    public ApiResponse<ExceptionCaseResponse> transition(@PathVariable Long exceptionId, @Valid @RequestBody ExceptionStatusRequest request,
            @RequestHeader("Idempotency-Key") String key, @RequestHeader(value = "X-Request-Id", required = false) String requestId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.transitionException(tenant(jwt), user(jwt), exceptionId, request, key, requestId));
    }

    @GetMapping("/exceptions/{exceptionId}/handling-records")
    public ApiResponse<List<HandlingRecordResponse>> handlingRecords(@PathVariable Long exceptionId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.listHandlingRecords(tenant(jwt), user(jwt), exceptionId));
    }

    @PostMapping("/exceptions/{exceptionId}/handling-records")
    public ResponseEntity<ApiResponse<HandlingRecordResponse>> addHandlingRecord(@PathVariable Long exceptionId,
            @Valid @RequestBody CreateHandlingRecordRequest request, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.addHandlingRecord(tenant(jwt), user(jwt), exceptionId, request, key, requestId)));
    }

    @GetMapping("/exceptions/{exceptionId}/evidence")
    public ApiResponse<List<EvidenceAttachmentResponse>> evidence(@PathVariable Long exceptionId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.listEvidence(tenant(jwt), user(jwt), exceptionId));
    }

    @PostMapping(value = "/exceptions/{exceptionId}/evidence", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ApiResponse<EvidenceAttachmentResponse>> uploadEvidence(@PathVariable Long exceptionId,
            @RequestPart("file") MultipartFile file, @RequestPart(value = "description", required = false) String description,
            @RequestPart("version") Long version, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.uploadEvidence(tenant(jwt), user(jwt), exceptionId, file, description, version, key, requestId)));
    }

    @GetMapping("/exceptions/{exceptionId}/evidence/{attachmentId}/content")
    public ResponseEntity<byte[]> downloadEvidence(@PathVariable Long exceptionId, @PathVariable Long attachmentId,
                                                   @AuthenticationPrincipal Jwt jwt) {
        var attachment = service.downloadEvidence(tenant(jwt), user(jwt), exceptionId, attachmentId);
        MediaType mediaType;
        try { mediaType = MediaType.parseMediaType(attachment.contentType()); }
        catch (Exception ignored) { mediaType = MediaType.APPLICATION_OCTET_STREAM; }
        return ResponseEntity.ok().contentType(mediaType).contentLength(attachment.fileSize())
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(attachment.originalFileName(), StandardCharsets.UTF_8).build().toString())
                .body(attachment.content());
    }

    @PostMapping("/exceptions/{exceptionId}/claim")
    public ResponseEntity<ApiResponse<ClaimResponse>> createClaim(@PathVariable Long exceptionId, @Valid @RequestBody CreateClaimRequest request,
            @RequestHeader("Idempotency-Key") String key, @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @AuthenticationPrincipal Jwt jwt) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createClaim(tenant(jwt), user(jwt), exceptionId, request, key, requestId)));
    }

    @GetMapping("/claims/{claimId}")
    public ApiResponse<ClaimResponse> getClaim(@PathVariable Long claimId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.getClaim(tenant(jwt), user(jwt), claimId));
    }

    @PostMapping("/claims/{claimId}/submit")
    public ApiResponse<ClaimResponse> submit(@PathVariable Long claimId, @Valid @RequestBody ClaimActionRequest request,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.submitClaim(tenant(jwt), user(jwt), claimId, request, key, requestId));
    }

    @PostMapping("/claims/{claimId}/result")
    public ApiResponse<ClaimResponse> result(@PathVariable Long claimId, @Valid @RequestBody ClaimResultRequest request,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.resolveClaim(tenant(jwt), user(jwt), claimId, request, key, requestId));
    }

    @PostMapping("/claims/{claimId}/finance-confirmation")
    public ApiResponse<ClaimResponse> financeConfirmation(@PathVariable Long claimId,
            @Valid @RequestBody FinanceConfirmRequest request, @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.financeConfirmClaim(tenant(jwt), user(jwt), claimId, request, key, requestId));
    }

    @PostMapping("/claims/{claimId}/close")
    public ApiResponse<ClaimResponse> close(@PathVariable Long claimId, @Valid @RequestBody ClaimActionRequest request,
            @RequestHeader("Idempotency-Key") String key,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.closeClaim(tenant(jwt), user(jwt), claimId, request, key, requestId));
    }

    private Long tenant(Jwt jwt) { try { return Long.valueOf(jwt.getClaimAsString("tenant_id")); } catch (Exception e) { throw new ExceptionClaimException("COMMON-1004", 403); } }
    private Long user(Jwt jwt) { try { return Long.valueOf(jwt.getSubject()); } catch (Exception e) { throw new ExceptionClaimException("COMMON-1004", 403); } }
}
