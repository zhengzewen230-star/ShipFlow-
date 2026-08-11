package com.shipflow.tracking.api;

import com.shipflow.common.api.ApiResponse;
import com.shipflow.tracking.api.model.TrackingCallbackResult;
import com.shipflow.tracking.application.TrackingCallbackApplicationService;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/integrations/logistics/{providerCode}/tracking-events")
public class TrackingCallbackController {
    private final TrackingCallbackApplicationService service;

    public TrackingCallbackController(TrackingCallbackApplicationService service) {
        this.service = service;
    }

    @PostMapping(consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<ApiResponse<TrackingCallbackResult>> receive(
            @PathVariable String providerCode,
            @RequestHeader(value = "X-Provider-Timestamp", required = false) String timestamp,
            @RequestHeader(value = "X-Provider-Signature", required = false) String signature,
            @RequestHeader(value = "X-Request-Id", required = false) String requestId,
            @RequestBody byte[] rawRequestBody) {
        TrackingCallbackResult result = service.receive(providerCode, timestamp, signature,
                rawRequestBody, requestId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(ApiResponse.success(result));
    }
}
