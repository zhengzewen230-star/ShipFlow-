package com.shipflow.rbac.application;

public class RbacException extends RuntimeException {
    private final String code;
    private final int status;
    public RbacException(String code, int status) { super(code); this.code = code; this.status = status; }
    public String code() { return code; }
    public int status() { return status; }
}
