package com.shipflow.onboarding.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.onboarding.api.model.*;
import com.shipflow.onboarding.application.OnboardingApplicationService;
import com.shipflow.onboarding.domain.model.*;
import jakarta.validation.Valid;
import org.springframework.http.*;
import org.springframework.context.annotation.Profile;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@Profile("!test")
public class OnboardingController {
    private final OnboardingApplicationService service;
    public OnboardingController(OnboardingApplicationService service) { this.service = service; }
    @PostMapping("/public/estimate-requests")
    public ResponseEntity<ApiResponse<GuestEstimateResponse>> estimate(@Valid @RequestBody CreateGuestEstimateRequest request,
            @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.submitEstimate(request,key)));
    }
    @PostMapping("/public/onboarding-applications")
    public ResponseEntity<ApiResponse<OnboardingApplication>> apply(@Valid @RequestBody CreateOnboardingApplicationRequest request,
            @RequestHeader(value="Idempotency-Key",required=false) String key) {
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.apply(request,key)));
    }
    @PostMapping("/public/onboarding-activations")
    public ResponseEntity<ApiResponse<ActivationResponse>> activate(@Valid @RequestBody ActivationRequest request) {
        return ResponseEntity.ok(ApiResponse.success(service.activate(request)));
    }
    @GetMapping("/platform/onboarding-applications")
    public ResponseEntity<ApiResponse<OnboardingPage>> list(@RequestParam(required=false) String status,@RequestParam(defaultValue="1") int page,@RequestParam(defaultValue="20") int pageSize) { return ResponseEntity.ok(ApiResponse.success(service.list(status,page,pageSize))); }
    @GetMapping("/platform/guest-estimate-leads")
    public ResponseEntity<ApiResponse<GuestEstimateLeadPage>> listEstimateLeads(@RequestParam(required=false) String status, @RequestParam(required=false) String keyword,
            @RequestParam(required=false) java.time.OffsetDateTime from, @RequestParam(required=false) java.time.OffsetDateTime to,
            @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) { return ResponseEntity.ok(ApiResponse.success(service.listEstimateLeads(status, keyword, from, to, page, pageSize))); }
    @GetMapping("/platform/guest-estimate-leads/{leadId}")
    public ResponseEntity<ApiResponse<GuestEstimateLead>> getEstimateLead(@PathVariable Long leadId) { return ResponseEntity.ok(ApiResponse.success(service.getEstimateLead(leadId))); }
    @PatchMapping("/platform/guest-estimate-leads/{leadId}/status")
    public ResponseEntity<ApiResponse<GuestEstimateLead>> updateEstimateLeadStatus(@PathVariable Long leadId, @Valid @RequestBody UpdateGuestEstimateLeadStatusRequest request,
            @RequestHeader(value="X-Request-Id", required=false) String requestId, @AuthenticationPrincipal Jwt jwt) { return ResponseEntity.ok(ApiResponse.success(service.updateEstimateLeadStatus(leadId, request, userId(jwt), requestId))); }
    @GetMapping("/platform/onboarding-applications/{applicationId}")
    public ResponseEntity<ApiResponse<OnboardingApplication>> get(@PathVariable Long applicationId) { return ResponseEntity.ok(ApiResponse.success(service.get(applicationId))); }
    @PostMapping("/platform/onboarding-applications/{applicationId}/approve")
    public ResponseEntity<ApiResponse<ApprovalResponse>> approve(@PathVariable Long applicationId,@Valid @RequestBody ApproveOnboardingApplicationRequest request,
            @RequestHeader(value="X-Request-Id",required=false) String requestId,@AuthenticationPrincipal Jwt jwt) {
        OnboardingApplicationService.ApprovalResult result=service.approve(applicationId,request,userId(jwt),requestId);
        return ResponseEntity.ok(ApiResponse.success(new ApprovalResponse(result.application(),result.invitationToken())));
    }
    @PostMapping("/platform/onboarding-applications/{applicationId}/reject")
    public ResponseEntity<ApiResponse<OnboardingApplication>> reject(@PathVariable Long applicationId,@Valid @RequestBody RejectOnboardingApplicationRequest request,@AuthenticationPrincipal Jwt jwt) { return ResponseEntity.ok(ApiResponse.success(service.reject(applicationId,request,userId(jwt)))); }
    private Long userId(Jwt jwt){return jwt==null||jwt.getSubject()==null?null:Long.valueOf(jwt.getSubject());}
    /** The opaque token is returned only once to an authorised reviewer for secure out-of-band delivery. */
    public record ApprovalResponse(OnboardingApplication application, String invitationToken) {}
}
