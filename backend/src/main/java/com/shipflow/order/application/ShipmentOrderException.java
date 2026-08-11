package com.shipflow.order.application;

public class ShipmentOrderException extends RuntimeException {
    private final String code; private final int status;
    public ShipmentOrderException(String code, int status) { this.code = code; this.status = status; }
    public String code() { return code; } public int status() { return status; }
}
