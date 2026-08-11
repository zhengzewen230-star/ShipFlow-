package com.shipflow.logistics.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.logistics.application.LogisticsMasterApplicationService;
import com.shipflow.logistics.domain.model.LogisticsChannel;
import com.shipflow.logistics.domain.model.LogisticsChannelPage;
import com.shipflow.logistics.domain.model.PublishedPriceRule;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Tenant-visible, read-only public logistics catalogue. */
@RestController
@RequestMapping("/api/v1/logistics/channels")
public class TenantLogisticsController {
    private final LogisticsMasterApplicationService service;
    public TenantLogisticsController(LogisticsMasterApplicationService service) { this.service = service; }
    @GetMapping public ApiResponse<LogisticsChannelPage> channels(@RequestParam(required = false) String countryCode, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize) { return ApiResponse.success(service.availableChannels(countryCode, page, pageSize)); }
    @GetMapping("/{channelId}") public ApiResponse<LogisticsChannel> channel(@PathVariable Long channelId) { return ApiResponse.success(service.availableChannel(channelId)); }
    @GetMapping("/{channelId}/service-countries") public ApiResponse<java.util.List<String>> serviceCountries(@PathVariable Long channelId) { return ApiResponse.success(service.availableChannel(channelId).serviceCountries()); }
    @GetMapping("/{channelId}/price-rule") public ApiResponse<PublishedPriceRule> effectivePriceRule(@PathVariable Long channelId) { return ApiResponse.success(service.effectivePublishedPriceRule(channelId)); }
}
