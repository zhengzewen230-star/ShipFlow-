package com.shipflow.tracking.application;

public class TrackingCallbackException extends RuntimeException {
    private final String code;
    private final int status;

    public TrackingCallbackException(String code, int status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public String code() {
        return code;
    }

    public int status() {
        return status;
    }
}
