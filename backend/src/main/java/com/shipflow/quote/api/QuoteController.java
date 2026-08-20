package com.shipflow.quote.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.quote.api.model.QuotePageResponse;
import com.shipflow.quote.api.model.QuoteResponse;
import com.shipflow.quote.api.model.QuoteValidationResponse;
import com.shipflow.quote.application.QuoteException;
import com.shipflow.quote.application.QuoteCreationApplicationService;
import com.shipflow.quote.application.QuoteQueryApplicationService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.format.annotation.DateTimeFormat;
import java.time.LocalDateTime;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {
    private final QuoteQueryApplicationService service;
    private final QuoteCreationApplicationService creationService;

    public QuoteController(QuoteQueryApplicationService service, QuoteCreationApplicationService creationService) {
        this.service = service;
        this.creationService = creationService;
    }

    @PostMapping
    public org.springframework.http.ResponseEntity<ApiResponse<QuoteResponse>> createQuote(
            @jakarta.validation.Valid @RequestBody com.shipflow.quote.api.model.CreateQuoteRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @AuthenticationPrincipal Jwt jwt) {
        return org.springframework.http.ResponseEntity.status(org.springframework.http.HttpStatus.CREATED)
                .body(ApiResponse.success(creationService.create(tenant(jwt), Long.valueOf(jwt.getSubject()), request,
                        idempotencyKey, requestId)));
    }

    @GetMapping
    public ApiResponse<QuotePageResponse> listQuotes(
            @RequestParam(required = false) String quoteNo,
            @RequestParam(required = false) Long storeId,
            @RequestParam(required = false) Long channelId,
            @RequestParam(required = false) String destinationCountry,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime createdTo,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime validFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime validTo,
            @RequestParam(defaultValue = "createdAt") String sortField,
            @RequestParam(defaultValue = "DESC") String sortDirection,
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "20") int pageSize,
            @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.list(tenant(jwt), user(jwt), quoteNo, storeId, channelId, destinationCountry, status, createdFrom, createdTo, validFrom, validTo, sortField, sortDirection, page, pageSize));
    }

    @GetMapping("/{quoteId}")
    public ApiResponse<QuoteResponse> getQuote(@PathVariable Long quoteId, @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.get(tenant(jwt), user(jwt), quoteId));
    }

    @PostMapping("/{quoteId}/validate")
    public ApiResponse<QuoteValidationResponse> validateQuote(@PathVariable Long quoteId,
                                                               @AuthenticationPrincipal Jwt jwt) {
        return ApiResponse.success(service.validate(tenant(jwt), user(jwt), quoteId));
    }

    private Long tenant(Jwt jwt) {
        String claim = jwt == null ? null : jwt.getClaimAsString("tenant_id");
        try {
            return claim == null ? null : Long.valueOf(claim);
        } catch (NumberFormatException exception) {
            throw new QuoteException("COMMON-1004", 403);
        }
    }
    private Long user(Jwt jwt) { try { return jwt == null ? null : Long.valueOf(jwt.getSubject()); } catch (NumberFormatException exception) { throw new QuoteException("COMMON-1004", 403); } }
}
