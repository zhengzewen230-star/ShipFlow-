package com.shipflow.tenant.application;

public class TenantException extends RuntimeException {
    private final String code;
    private final int status;

    public TenantException(String code, int status) {
        super(code);
        this.code = code;
        this.status = status;
    }

    public String code() { return code; }
    public int status() { return status; }
}
