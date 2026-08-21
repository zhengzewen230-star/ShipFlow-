package com.shipflow.user.application;

public class UserException extends RuntimeException {
    private final String code;
    private final int status;
    public UserException(String code, int status) { super(code); this.code = code; this.status = status; }
    public String code() { return code; }
    public int status() { return status; }
}
