package com.shipflow.common.api;

import com.shipflow.common.trace.TraceId;

import java.util.Map;

public record ApiErrorResponse(boolean success, String traceId, ApiError error) {

    public ApiErrorResponse(String code, String message) {
        this(false, TraceId.current(), new ApiError(code, message, Map.of()));
    }

    public ApiErrorResponse(String code, String message, java.util.Map<String, Object> details) {
        this(false, TraceId.current(), new ApiError(code, message, details));
    }
}
