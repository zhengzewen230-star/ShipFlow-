package com.shipflow.logistics.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.logistics.application.LogisticsMasterApplicationService;
import com.shipflow.logistics.domain.model.PublicLogisticsChannel;
import com.shipflow.logistics.domain.model.PublicLogisticsChannelPage;
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
    @GetMapping public ApiResponse<PublicLogisticsChannelPage> channels(@RequestParam(required = false) String channelCode, @RequestParam(required = false) String channelName, @RequestParam(required = false) String serviceCountry, @RequestParam(required = false) String status, @RequestParam(defaultValue = "1") int page, @RequestParam(defaultValue = "20") int pageSize, @RequestParam(defaultValue = "updatedAt") String sortField, @RequestParam(defaultValue = "DESC") String sortDirection) { return ApiResponse.success(service.publicChannels(channelCode, channelName, serviceCountry, status, page, pageSize, sortField, sortDirection)); }
    @GetMapping("/{channelId}") public ApiResponse<PublicLogisticsChannel> channel(@PathVariable Long channelId) { return ApiResponse.success(service.publicChannel(channelId)); }
    @GetMapping("/{channelId}/service-countries") public ApiResponse<java.util.List<String>> serviceCountries(@PathVariable Long channelId) { return ApiResponse.success(service.publicChannel(channelId).serviceCountries()); }
}
