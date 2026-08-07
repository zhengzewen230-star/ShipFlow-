package com.shipflow.common.trace;

import org.slf4j.MDC;

import java.util.UUID;

public final class TraceId {

    public static final String HEADER_NAME = "X-Trace-Id";
    public static final String MDC_KEY = "traceId";

    private TraceId() {
    }

    public static String current() {
        String traceId = MDC.get(MDC_KEY);
        return traceId == null || traceId.isBlank() ? "unknown" : traceId;
    }

    public static String currentOrCreate() {
        String traceId = current();
        if (!"unknown".equals(traceId)) {
            return traceId;
        }
        String generated = UUID.randomUUID().toString();
        MDC.put(MDC_KEY, generated);
        return generated;
    }
}
