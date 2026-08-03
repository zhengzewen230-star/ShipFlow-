package com.shipflow.common.trace;

import org.slf4j.MDC;

public final class TraceId {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private TraceId() {
    }

    public static String current() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null || traceId.isBlank() ? "unknown" : traceId;
    }
}
