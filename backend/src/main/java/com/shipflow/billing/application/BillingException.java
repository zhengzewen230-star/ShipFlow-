package com.shipflow.billing.application;

public class BillingException extends RuntimeException {
    private final String code; private final int status;
    public BillingException(String code, int status) { this.code=code; this.status=status; }
    public String code() { return code; } public int status() { return status; }
}
