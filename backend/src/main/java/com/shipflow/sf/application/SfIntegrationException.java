package com.shipflow.sf.application;

public class SfIntegrationException extends RuntimeException {
    private final String code;
    private final int status;

    public SfIntegrationException(String code, int status, String message) {
        super(message);
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
