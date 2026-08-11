package com.shipflow.logistics.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.logistics.api.model.*;
import com.shipflow.logistics.application.LogisticsMasterApplicationService;
import com.shipflow.logistics.domain.model.*;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;

@RestController
@RequestMapping("/api/v1/platform")
public class LogisticsMasterController {
    private final LogisticsMasterApplicationService service;
    public LogisticsMasterController(LogisticsMasterApplicationService service) { this.service = service; }
    @GetMapping("/logistics-providers") public ApiResponse<LogisticsProviderPage> providers(@RequestParam(required=false) String status, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) { return ApiResponse.success(service.providers(status, page, pageSize)); }
    @PostMapping("/logistics-providers") public ResponseEntity<ApiResponse<LogisticsProvider>> createProvider(@Valid @RequestBody CreateLogisticsProviderRequest request,@RequestHeader("Idempotency-Key") String key,@RequestHeader(value="X-Request-Id",required=false) String requestId,@AuthenticationPrincipal Jwt jwt) { return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createProvider(request,key,Long.valueOf(jwt.getSubject()),requestId))); }
    @GetMapping("/logistics-providers/{providerId}") public ApiResponse<LogisticsProvider> provider(@PathVariable Long providerId) { return ApiResponse.success(service.provider(providerId)); }
    @PutMapping("/logistics-providers/{providerId}") public ApiResponse<LogisticsProvider> updateProvider(@PathVariable Long providerId, @Valid @RequestBody UpdateLogisticsProviderRequest request,@RequestHeader(value="X-Request-Id",required=false) String requestId,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(service.updateProvider(providerId, request,Long.valueOf(jwt.getSubject()),requestId)); }
    @GetMapping("/logistics-channels") public ApiResponse<LogisticsChannelPage> channels(@RequestParam(required=false) Long providerId, @RequestParam(required=false) String status, @RequestParam(defaultValue="1") int page, @RequestParam(defaultValue="20") int pageSize) { return ApiResponse.success(service.channels(providerId, status, page, pageSize)); }
    @PostMapping("/logistics-channels") public ResponseEntity<ApiResponse<LogisticsChannel>> createChannel(@Valid @RequestBody CreateLogisticsChannelRequest request,@RequestHeader("Idempotency-Key") String key,@RequestHeader(value="X-Request-Id",required=false) String requestId,@AuthenticationPrincipal Jwt jwt) { return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.createChannel(request,key,Long.valueOf(jwt.getSubject()),requestId))); }
    @GetMapping("/logistics-channels/{channelId}") public ApiResponse<LogisticsChannel> channel(@PathVariable Long channelId) { return ApiResponse.success(service.channel(channelId)); }
    @PutMapping("/logistics-channels/{channelId}") public ApiResponse<LogisticsChannel> updateChannel(@PathVariable Long channelId, @Valid @RequestBody UpdateLogisticsChannelRequest request,@RequestHeader(value="X-Request-Id",required=false) String requestId,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(service.updateChannel(channelId, request,Long.valueOf(jwt.getSubject()),requestId)); }
    @PutMapping("/logistics-channels/{channelId}/service-countries") public ApiResponse<LogisticsChannel> replaceCountries(@PathVariable Long channelId, @Valid @RequestBody ServiceCountriesRequest request,@RequestHeader(value="X-Request-Id",required=false) String requestId,@AuthenticationPrincipal Jwt jwt) { return ApiResponse.success(service.replaceServiceCountries(channelId, request,Long.valueOf(jwt.getSubject()),requestId)); }
    @PostMapping("/logistics-channels/{channelId}/price-rules") public ResponseEntity<ApiResponse<PublishedPriceRule>> publishPriceRule(@PathVariable Long channelId, @Valid @RequestBody PublishPriceRuleRequest request,@RequestHeader("Idempotency-Key") String key,@RequestHeader(value="X-Request-Id",required=false) String requestId,@AuthenticationPrincipal Jwt jwt) { return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(service.publishPriceRule(channelId, request,key,Long.valueOf(jwt.getSubject()),requestId))); }
}
