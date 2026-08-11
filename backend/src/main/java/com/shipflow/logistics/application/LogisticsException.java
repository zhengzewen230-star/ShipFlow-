package com.shipflow.logistics.application;

public class LogisticsException extends RuntimeException {
    private final String code;
    private final int status;
    public LogisticsException(String code, int status) { this.code = code; this.status = status; }
    public String code() { return code; }
    public int status() { return status; }
}
