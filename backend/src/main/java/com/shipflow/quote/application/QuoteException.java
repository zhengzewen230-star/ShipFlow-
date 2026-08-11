package com.shipflow.quote.application;

public class QuoteException extends RuntimeException {
    private final String code;
    private final int status;

    public QuoteException(String code, int status) {
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
