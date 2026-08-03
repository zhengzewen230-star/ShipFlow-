package com.shipflow.common.api;

import com.shipflow.common.trace.TraceId;

public record ApiResponse<T>(boolean success, String traceId, String message, T data) {

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(true, TraceId.current(), "OK", data);
    }
}
